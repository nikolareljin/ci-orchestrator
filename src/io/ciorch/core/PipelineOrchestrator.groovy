package io.ciorch.core

import java.io.Serializable
import io.ciorch.git.WebhookParser
import io.ciorch.git.GitEvent
import io.ciorch.git.MatrixLoader
import io.ciorch.git.MatrixEvaluator
import io.ciorch.git.GitOperations
import io.ciorch.git.TaskType
import io.ciorch.build.BuildAdapter
import io.ciorch.build.DockerAdapter
import io.ciorch.build.adapters.NodeAdapter
import io.ciorch.build.adapters.GoAdapter
import io.ciorch.build.adapters.PhpAdapter
import io.ciorch.build.adapters.PythonAdapter
import io.ciorch.build.adapters.CSharpAdapter
import io.ciorch.build.adapters.RustAdapter
import io.ciorch.build.adapters.CppAdapter
import io.ciorch.build.adapters.JavaAdapter
import io.ciorch.build.adapters.GenericAdapter
import io.ciorch.deploy.DeployAdapter

class PipelineOrchestrator implements Serializable {
    def context = null
    Config config = null
    Notifier notifier = null
    SystemCall system = null
    GitOperations gitOps = null

    // Adapter registry: id → class (ConcurrentHashMap for parallel-pipeline safety)
    private static final Map<String, Class> BUILD_REGISTRY = new java.util.concurrent.ConcurrentHashMap<>([
        docker:  DockerAdapter,
        node:    NodeAdapter,
        go:      GoAdapter,
        php:     PhpAdapter,
        python:  PythonAdapter,
        csharp:  CSharpAdapter,
        rust:    RustAdapter,
        cpp:     CppAdapter,
        java:    JavaAdapter,
        generic: GenericAdapter
    ])
    private static final Map<String, Class> DEPLOY_REGISTRY = new java.util.concurrent.ConcurrentHashMap<>()

    // Runtime state
    GitEvent currentEvent = null
    List<String> pendingTasks = []
    private BuildAdapter _cachedAdapter = null

    PipelineOrchestrator(def context, Config config, SystemCall system) {
        this.context = context
        this.config = config
        this.system = system
        this.notifier = new Notifier(context, config.slackChannel, config.slackToken)
        this.gitOps = new GitOperations(context, system)
    }

    // Main entry: process a raw webhook payload JSON string
    boolean process(String payloadJson, String apiToken = "", String apiUser = "") {
        Map json = null
        try {
            json = new groovy.json.JsonSlurperClassic().parseText(payloadJson) as Map
        } catch (Exception ex) {
            notifier.log("PipelineOrchestrator: failed to parse payload: ${ex.message}", Notifier.ERROR)
            return false
        }
        return processMap(json, apiToken, apiUser)
    }

    // Process an already-parsed payload Map
    boolean processMap(Map payload, String apiToken = "", String apiUser = "") {
        // 1. Parse the webhook
        WebhookParser parser = new WebhookParser(context, payload, apiToken, apiUser)
        parser.setValues(false)

        if (!parser.shouldBeProcessed) {
            notifier.log("PipelineOrchestrator: payload does not require processing", Notifier.INFO)
            return true
        }

        currentEvent = parser.toGitEvent()
        notifier.log("PipelineOrchestrator: event=${currentEvent}", Notifier.INFO)

        // 2. Load branching strategy matrix
        Map matrixData = _loadMatrix()
        MatrixEvaluator evaluator = new MatrixEvaluator(matrixData)

        // 3. Evaluate task list
        pendingTasks = evaluator.evaluateEvent(currentEvent)
        notifier.log("PipelineOrchestrator: tasks=${pendingTasks}", Notifier.INFO)

        if (!pendingTasks) {
            notifier.log("PipelineOrchestrator: no tasks matched for this event", Notifier.INFO)
            return true
        }

        // 4. Dispatch tasks
        return _dispatchTasks(pendingTasks)
    }

    // Execute tasks in order
    private boolean _dispatchTasks(List<String> tasks) {
        for (String task : tasks) {
            notifier.log("PipelineOrchestrator: running task [${task}]", Notifier.INFO)
            boolean ok = _runTask(task)
            if (!ok) {
                notifier.log("PipelineOrchestrator: task [${task}] failed — halting", Notifier.ERROR)
                notifier.notify("Pipeline failed at task: ${task}", Notifier.ERROR)
                return false
            }
        }
        notifier.notify("Pipeline completed: ${pendingTasks.join(' → ')}", Notifier.SUCCESS)
        return true
    }

    private boolean _runTask(String task) {
        switch (task) {
            case TaskType.LINT:
                return _runBuildStep(task)
            case TaskType.TEST:
                return _runBuildStep(task)
            case TaskType.BUILD:
                return _runBuildStep(task)
            case TaskType.E2E:
                notifier.log("E2E task: override _runTask() or provide e2e command", Notifier.WARN)
                return true
            case TaskType.DEPLOY:
                return _runDeploy()
            case TaskType.TAG:
                return _runTag()
            case TaskType.NOTIFY:
                notifier.notify("Pipeline reached notify step", Notifier.INFO)
                return true
            case TaskType.COMMIT:
                return _runCommit()
            case TaskType.RELEASE:
                return _runRelease()
            default:
                notifier.log("PipelineOrchestrator: unknown task [${task}] — skipping", Notifier.WARN)
                return true
        }
    }

    private boolean _runBuildStep(String step) {
        BuildAdapter adapter = _resolveBuildAdapter()
        if (!adapter) return false

        switch (step) {
            case TaskType.LINT: return adapter.lint([:])
            case TaskType.TEST: return adapter.test([:])
            case TaskType.BUILD: return adapter.build([:])
            default: return true
        }
    }

    private boolean _runDeploy() {
        DeployAdapter adapter = _resolveDeployAdapter()
        if (!adapter) {
            notifier.log("PipelineOrchestrator: no deploy adapter for '${config.deployAdapter}'", Notifier.WARN)
            return true  // non-fatal if not configured
        }
        String env = _targetEnvironment()
        if (!adapter.validate([:], context)) return false
        if (!adapter.preDeploy([:], env)) return false
        if (!adapter.deploy([:], env)) return false
        return adapter.postDeploy([:], env)
    }

    private boolean _runTag() {
        String tagName = currentEvent?.versionString ?: ""
        if (!tagName) {
            notifier.log("PipelineOrchestrator: no version to tag", Notifier.WARN)
            return true
        }
        return gitOps.tag(tagName, "Release ${tagName}")
    }

    private boolean _runCommit() {
        return gitOps.commit("ci: automated pipeline update [${currentEvent?.eventType}]")
    }

    private boolean _runRelease() {
        return _runBuildStep(TaskType.BUILD)
    }

    private BuildAdapter _resolveBuildAdapter() {
        if (_cachedAdapter != null) return _cachedAdapter

        String adapterName = config.buildAdapter ?: "docker"
        Class adapterClass = BUILD_REGISTRY[adapterName]
        if (!adapterClass) {
            notifier.log("PipelineOrchestrator: unknown build adapter '${adapterName}'", Notifier.WARN)
            return null
        }
        BuildAdapter adapter = adapterClass.newInstance(context, system, config) as BuildAdapter
        if (!adapter.prepare([:], context)) {
            notifier.log("PipelineOrchestrator: adapter '${adapterName}' prepare() failed — aborting build steps", Notifier.ERROR)
            return null
        }
        _cachedAdapter = adapter
        return _cachedAdapter
    }

    private DeployAdapter _resolveDeployAdapter() {
        String adapterName = config.deployAdapter ?: ""
        if (!adapterName) return null
        Class adapterClass = DEPLOY_REGISTRY[adapterName]
        if (!adapterClass) return null
        return adapterClass.newInstance(context, system, config) as DeployAdapter
    }

    private Map _loadMatrix() {
        String strategy = config.branchingStrategy ?: "default-gitflow"
        Map builtin = MatrixLoader.loadBuiltin(strategy, context)
        if (config.customMatrixPath) {
            Map custom = MatrixLoader.loadCustom(config.customMatrixPath, context)
            return MatrixLoader.merge(builtin, custom)
        }
        return builtin
    }

    private String _targetEnvironment() {
        // Map dst branch type to environment name
        String dstBranch = currentEvent?.dstBranch ?: ""
        if (dstBranch.contains("prod") || dstBranch.contains("master") || dstBranch.contains("main")) {
            return "production"
        }
        if (dstBranch.contains("qa") || dstBranch.contains("release") || dstBranch.contains("staging")) {
            return "staging"
        }
        return "development"
    }

    // Register a custom build adapter class
    static void registerBuildAdapter(String id, Class adapterClass) {
        BUILD_REGISTRY[id] = adapterClass
    }

    // Register a custom deploy adapter class
    static void registerDeployAdapter(String id, Class adapterClass) {
        DEPLOY_REGISTRY[id] = adapterClass
    }
}
