package io.ciorch.core

import java.io.Serializable
import groovy.json.JsonOutput

class Notifier implements Serializable {
    def context = null
    String slackChannel = ""
    String slackToken = ""
    String webhookUrl = ""

    // Log levels
    static final String INFO = "info"
    static final String WARN = "warn"
    static final String ERROR = "error"
    static final String SUCCESS = "success"
    static final String DEFAULT = "default"

    Notifier(def context, String slackChannel = "", String slackToken = "", String webhookUrl = "") {
        this.context = context
        this.slackChannel = slackChannel
        this.slackToken = slackToken
        this.webhookUrl = webhookUrl
    }

    // Log a message to console
    void log(String message, String level = DEFAULT) {
        String prefix = level != DEFAULT ? "[${level.toUpperCase()}] " : ""
        context?.echo("${prefix}${message}")
    }

    // Send a Slack notification (requires slackSend Jenkins plugin)
    void slack(String message, String color = "good") {
        if (!slackChannel || !slackToken) {
            log("Notifier: Slack not configured, skipping notification", WARN)
            return
        }
        try {
            context?.slackSend(
                channel: slackChannel,
                token: slackToken,
                color: color,
                message: message
            )
        } catch (Exception ex) {
            log("Notifier: Slack notification failed: ${ex.message}", WARN)
        }
    }

    // Send a notification (Slack + optional webhook)
    void notify(String message, String level = DEFAULT) {
        log(message, level)
        String color = levelToSlackColor(level)
        slack(message, color)
        if (webhookUrl) {
            sendWebhook([message: message, level: level])
        }
    }

    // Send a raw JSON payload to a webhook URL
    void sendWebhook(Map payload) {
        if (!webhookUrl) return
        try {
            String json = JsonOutput.toJson(payload)
            context?.withEnv(["CIORCH_WEBHOOK_URL=${webhookUrl}", "CIORCH_WEBHOOK_BODY=${json}"]) {
                context?.sh(
                    script: 'curl -s -X POST "$CIORCH_WEBHOOK_URL" -H "Content-Type: application/json" -d "$CIORCH_WEBHOOK_BODY" > /dev/null',
                    returnStatus: true
                )
            }
        } catch (Exception ex) {
            log("Notifier: webhook failed: ${ex.message}", WARN)
        }
    }

    private String levelToSlackColor(String level) {
        switch (level) {
            case ERROR: return "danger"
            case WARN: return "warning"
            case SUCCESS: return "good"
            default: return "#439FE0"
        }
    }
}
