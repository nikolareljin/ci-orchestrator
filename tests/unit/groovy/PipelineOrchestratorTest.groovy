package io.ciorch.tests

import io.ciorch.build.BuildAdapter
import io.ciorch.core.Config
import io.ciorch.core.PipelineOrchestrator
import io.ciorch.core.SystemCall
import spock.lang.Specification

import java.lang.reflect.Method

class PipelineOrchestratorTest extends Specification {

    def mockContext = [
        echo: { msg -> },
        withEnv: { List<String> vars, Closure body -> body.call() }
    ]

    private SystemCall mockSystem(int returnCode = 0) {
        return new SystemCall(null, "", "", "", "") {
            @Override
            def run_command(String cmd, int mode) { return returnCode }
            @Override
            def run_command(String cmd, int mode, int timeout) { return returnCode }
        }
    }

    // Controllable adapter registered as a real Class so _resolveBuildAdapter can instantiate it.
    // Static counters are safe here because Spock runs tests sequentially.
    static class CountingAdapter implements BuildAdapter, Serializable {
        static int prepareCallCount = 0
        static boolean prepareResult = true

        CountingAdapter(def context, SystemCall system, Config config) {}

        @Override boolean prepare(Map cfg, def context) { prepareCallCount++; return prepareResult }
        @Override boolean lint(Map cfg)  { return true }
        @Override boolean test(Map cfg)  { return true }
        @Override boolean build(Map cfg) { return true }
        @Override List<String> getArtifacts() { return [] }
        @Override String getName() { return "counting" }

        static void reset(boolean result = true) { prepareCallCount = 0; prepareResult = result }
    }

    def setup() {
        PipelineOrchestrator.registerBuildAdapter("counting", CountingAdapter)
    }

    // Reflection helpers to access private methods without MOP arg-wrapping ambiguity
    private static boolean dispatchTasks(PipelineOrchestrator orch, List<String> tasks) {
        Method m = PipelineOrchestrator.class.getDeclaredMethod("_dispatchTasks", List.class)
        m.setAccessible(true)
        return m.invoke(orch, tasks) as boolean
    }

    private static boolean runBuildStep(PipelineOrchestrator orch, String step) {
        Method m = PipelineOrchestrator.class.getDeclaredMethod("_runBuildStep", String.class)
        m.setAccessible(true)
        return m.invoke(orch, step) as boolean
    }

    def "prepare() is called only once when multiple build steps run"() {
        given:
        CountingAdapter.reset(true)
        def cfg = new Config()
        cfg.buildAdapter = "counting"
        def orch = new PipelineOrchestrator(mockContext, cfg, mockSystem())

        when:
        dispatchTasks(orch, ["lint", "test", "build"])

        then:
        CountingAdapter.prepareCallCount == 1
    }

    def "prepare() failure aborts the build step and returns false"() {
        given:
        CountingAdapter.reset(false)
        def cfg = new Config()
        cfg.buildAdapter = "counting"
        def orch = new PipelineOrchestrator(mockContext, cfg, mockSystem())

        when:
        boolean result = runBuildStep(orch, "lint")

        then:
        result == false
        CountingAdapter.prepareCallCount == 1
    }

    def "prepare() failure halts dispatch after first failing task"() {
        given:
        CountingAdapter.reset(false)
        def cfg = new Config()
        cfg.buildAdapter = "counting"
        def orch = new PipelineOrchestrator(mockContext, cfg, mockSystem())

        when:
        boolean result = dispatchTasks(orch, ["lint", "test"])

        then:
        result == false
    }
}
