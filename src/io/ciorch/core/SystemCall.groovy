#!groovy

/**
 * System calls class.
 *
 * Runs shell commands and returns output or status codes.
 * Provides Git helpers, a cURL wrapper, and pipeline termination support.
 */
package io.ciorch.core

import groovy.json.JsonOutput
import java.io.Serializable

class SystemCall implements Serializable {
    public String githubToken
    public String githubUser
    public String tmpDir  = "tmp_ciorch"
    public String rootDir = "/workspace"

    /** Return the full stdout of the command. */
    static final int SHOW_COMMAND_OUTPUT = 0

    /** Return only the exit status code. */
    static final int SHOW_COMMAND_STATUS_VALUE = 1

    /**
     * Run the command and return 0 on success.
     * Raises an error if the command exits non-zero.
     */
    static final int SHOW_COMMAND_SUCCESS = -1

    /** Default command timeout in seconds (10 minutes). */
    static final int DEFAULT_TIMEOUT = 6000

    /** Reference to the Jenkins pipeline/job object. */
    public def job = null

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * @param job         Jenkins pipeline/job reference
     * @param githubUser  GitHub username for authenticated operations
     * @param githubToken GitHub personal access token
     * @param tmpDir      temporary workspace subdirectory name (default: tmp_ciorch)
     * @param rootDir     root workspace path on the build agent (default: /workspace)
     */
    SystemCall(def job, String githubUser, String githubToken,
               String tmpDir = "tmp_ciorch", String rootDir = "/workspace") {
        this.job         = job
        this.githubUser  = githubUser
        this.githubToken = githubToken
        this.tmpDir      = tmpDir
        this.rootDir     = rootDir
    }

    // -------------------------------------------------------------------------
    // Shell execution
    // -------------------------------------------------------------------------

    /**
     * Run a shell command and return its result.
     *
     * @param command     the shell command string to execute
     * @param status_type one of SHOW_COMMAND_OUTPUT | SHOW_COMMAND_STATUS_VALUE | SHOW_COMMAND_SUCCESS
     * @param timeout     maximum allowed elapsed time in seconds (default DEFAULT_TIMEOUT)
     * @return            stdout string, exit code integer, or 1 on error
     */
    def run_command(String command, int status_type = SHOW_COMMAND_OUTPUT, int timeout = DEFAULT_TIMEOUT) {
        def command_result = null
        def start = System.currentTimeMillis()

        this.job.echo "*** Running command: ${command} with status: ${status_type}" as String

        try {
            switch (status_type) {
                case SHOW_COMMAND_STATUS_VALUE:
                    command_result = this.job.sh(
                        script: "${command}" as String,
                        returnStatus: true
                    )
                    break
                case SHOW_COMMAND_SUCCESS:
                    int exitCode = this.job.sh(
                        script: "${command}" as String,
                        returnStatus: true
                    )
                    if (exitCode != 0) {
                        command_result = null
                        this.job.error "Command failed: ${command}"
                    } else {
                        command_result = exitCode
                    }
                    break
                case SHOW_COMMAND_OUTPUT:
                default:
                    command_result = this.job.sh(
                        script: "${command}" as String,
                        returnStdout: true
                    ).trim()
                    break
            }

            def end = System.currentTimeMillis()
            def elapsedTime = end - start

            this.job.echo "Bash command: ${command}"
            this.job.echo "Elapsed time: ${elapsedTime} ms"
            this.job.echo "Result: ${command_result}"

            // Post-execution warning only; use Jenkins timeout() step to enforce hard limits
            if (elapsedTime > timeout * 1000) {
                this.job.error "Command timeout: ${command}"
            }

        } catch (Exception err) {
            // sh() throws when exit status is non-zero; treat as failure.
            command_result = 1
            this.job.echo "Error with command: ${command}"
            this.job.echo "Error: ${err.toString()}"
        }

        return command_result
    }

    /**
     * Run a shell command that contains secrets, logging only a sanitised version.
     *
     * @param command          the actual shell command to execute (contains secrets)
     * @param truncated_command a redacted version safe to log
     * @param status_type      one of SHOW_COMMAND_OUTPUT | SHOW_COMMAND_STATUS_VALUE | SHOW_COMMAND_SUCCESS
     * @param timeout          maximum allowed elapsed time in seconds (default DEFAULT_TIMEOUT)
     * @return                 stdout string, exit code integer, or 1 on error
     */
    def run_command_with_secrets(String command, String truncated_command,
                                  int status_type = SHOW_COMMAND_OUTPUT, int timeout = DEFAULT_TIMEOUT) {
        def command_result = null
        def start = System.currentTimeMillis()

        this.job.echo "*** Running command with secrets: ${truncated_command} with status: ${status_type}" as String

        try {
            switch (status_type) {
                case SHOW_COMMAND_STATUS_VALUE:
                    command_result = this.job.sh(
                        script: "${command}" as String,
                        returnStatus: true
                    )
                    break
                case SHOW_COMMAND_SUCCESS:
                    int exitCode = this.job.sh(
                        script: "${command}" as String,
                        returnStatus: true
                    )
                    if (exitCode != 0) {
                        command_result = null
                        this.job.error "Command failed: ${truncated_command}"
                    } else {
                        command_result = exitCode
                    }
                    break
                case SHOW_COMMAND_OUTPUT:
                default:
                    command_result = this.job.sh(
                        script: "${command}" as String,
                        returnStdout: true
                    ).trim()
                    break
            }

            def end = System.currentTimeMillis()
            def elapsedTime = end - start

            this.job.echo "Bash command: ${truncated_command}"
            this.job.echo "Elapsed time: ${elapsedTime} ms"
            this.job.echo "Result: ${command_result}"

            if (elapsedTime > timeout * 1000) {
                this.job.error "Command timeout: ${truncated_command}"
            }

        } catch (Exception err) {
            command_result = 1
            this.job.echo "Error with command: ${truncated_command}"
            this.job.echo "Error: ${err.toString()}"
        }

        return command_result
    }

    // -------------------------------------------------------------------------
    // Git helpers
    // -------------------------------------------------------------------------

    /**
     * Run a git command using the configured credentials.
     *
     * Returns the stdout string result by default.
     * If status checking is needed, use run_command() directly instead.
     *
     * @param command the git command string
     * @param enforce one of SHOW_COMMAND_OUTPUT | SHOW_COMMAND_STATUS_VALUE | SHOW_COMMAND_SUCCESS
     * @return        command result or 0 on SHOW_COMMAND_SUCCESS with empty output
     */
    def git_command(String command, int enforce = SHOW_COMMAND_OUTPUT) {
        this.job.echo "${command}"

        def result = this.run_command(command.toString(), enforce as int)
        this.check_result(result as Object, command as String, enforce as int)

        // When enforce == SHOW_COMMAND_SUCCESS and result is 1 (error state treated as
        // "not found"), normalise to 0 to indicate the expected absence was confirmed.
        if (SHOW_COMMAND_SUCCESS == enforce && 1 == result) {
            return 0
        }

        return result
    }

    /**
     * Check the result of a shell command and halt the pipeline on failure.
     *
     * Intended to be used after run_command() with SHOW_COMMAND_STATUS_VALUE.
     *
     * @param result  the value returned by run_command()
     * @param command the original command string (for error messages)
     * @param enforce must be SHOW_COMMAND_STATUS_VALUE for the check to take effect
     * @return        0 on success, 1 on error (pipeline will also be halted on error)
     */
    int check_result(def result, String command, int enforce = SHOW_COMMAND_OUTPUT) {
        if (SHOW_COMMAND_STATUS_VALUE == enforce) {
            if (0 != result) {
                String errorMessage = "** ERROR in command: \t\t ${command}." as String
                this.job.echo errorMessage
                this.job.error "Halted."
                return 1
            }
        }
        return 0
    }

    /**
     * Configure git global identity settings on the build agent.
     *
     * @param email    git committer email address
     * @param username git committer display name
     */
    void set_git_config(String email, String username) {
        this.job.sh "git config --global user.email \"${email}\""
        this.job.sh "git config --global user.name \"${username}\""
        // Ignore file-mode (chmod) changes.
        this.job.sh "git config --global core.fileMode false"
        // Push the current branch to a branch of the same name on the remote.
        this.job.sh "git config --global push.default current"
    }

    // -------------------------------------------------------------------------
    // HTTP helper
    // -------------------------------------------------------------------------

    /**
     * Perform a POST cURL request authenticated with the configured GitHub credentials.
     *
     * @param endpoint URL to POST to
     * @param data     request body — either a String (sent as-is) or an object
     *                 (serialised to JSON automatically)
     */
    def curl_request(endpoint, data) {
        String json_data = ""
        if (data instanceof String) {
            json_data = data.toString()
        } else {
            json_data = JsonOutput.toJson(data)
        }

        String command = "curl -u ${this.githubUser}:${this.githubToken} -s '${endpoint.toString()}' -H 'Content-Type: application/json' -X POST -d '${json_data}'"
        String truncated = "curl -u ${this.githubUser}:[REDACTED] -s '${endpoint.toString()}' -H 'Content-Type: application/json' -X POST -d '${json_data}'"

        run_command_with_secrets(command, truncated, SHOW_COMMAND_STATUS_VALUE)
    }

    // -------------------------------------------------------------------------
    // Pipeline control
    // -------------------------------------------------------------------------

    /**
     * Stop the pipeline immediately with an error message.
     *
     * @param messageContent human-readable reason for termination
     */
    public terminate(String messageContent) {
        this.job.error "${messageContent}"
    }
}
