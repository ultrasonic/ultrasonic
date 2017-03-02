package org.moire.ultrasonic.api.subsonic.rules

import okhttp3.mockwebserver.MockWebServer
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Starts mock web server for test and shut it down after.
 */
class MockWebServerRule: TestRule {
    val mockWebServer = MockWebServer()

    override fun apply(base: Statement?, description: Description?): Statement {
        val ruleStatement = object: Statement() {
            override fun evaluate() {
                try {
                    mockWebServer.start()
                    base?.evaluate()
                } finally {
                    mockWebServer.shutdown()
                }
            }
        }
        return ruleStatement
    }
}