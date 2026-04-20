import io.ciorch.core.Config
import io.ciorch.core.SystemCall
import io.ciorch.core.PipelineOrchestrator

/**
 * ci-orchestrator main entry point.
 *
 * Usage in Jenkinsfile:
 *
 *   @Library('ci-orchestrator@production') _
 *   ciorch {
 *     // optional DSL overrides
 *   }
 *
 * Or trigger from a webhook parameter:
 *
 *   ciorch payload: params.PAYLOAD, configPath: 'ciorch.yml'
 */
def call(Map args = [:]) {
    def configPath = args.configPath ?: 'ciorch.yml'
    def payloadJson = args.payload ?: ''
    def apiToken = args.apiToken ?: ''
    def apiUser = args.apiUser ?: ''

    // Load configuration
    Config config = new Config(this)
    boolean loaded = config.load("${env.WORKSPACE}/${configPath}")
    if (!loaded) {
        echo "ciorch: ciorch.yml not found at ${configPath}, using defaults"
    }

    // Allow preset vars (e.g. ciorch_node) to supply a default adapter when ciorch.yml
    // does not specify one. Set the property directly to preserve all other loaded config.
    if (args.adapter && !config.buildAdapter) {
        config.buildAdapter = args.adapter as String
    }

    // Initialize system call helper
    SystemCall system = new SystemCall(
        this,
        apiUser,
        apiToken,
        'tmp_ciorch',
        env.WORKSPACE ?: '/workspace'
    )

    // Run pipeline
    PipelineOrchestrator orchestrator = new PipelineOrchestrator(this, config, system)

    if (payloadJson) {
        return orchestrator.process(payloadJson, apiToken, apiUser)
    } else {
        echo "ciorch: no payload provided — pipeline ready but not triggered"
        return true
    }
}
