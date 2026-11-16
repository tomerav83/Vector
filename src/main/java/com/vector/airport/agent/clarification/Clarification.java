package com.vector.airport.agent.clarification;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * The chat's way to ask the analyst back instead of answering, e.g. which Portland they mean: the question ends the
 * turn and reaches the page as the answer, marked {@code Answer.clarifying()}. The chat's only: an MCP client asks its
 * user in its own way.
 */
public class Clarification {

    @Tool(
            description = "Asks the analyst one question back, as your whole reply, only when you cannot answer without"
                    + " it: a name that fits several airports with nothing to choose between them. Every pick you"
                    + " offer must be one the tools can answer. Never for airports abroad, a deal's returns or live"
                    + " flight status: answer that the data does not cover them and what it can show instead. Never to confirm a pick you can state,"
                    + " nor to ask about periods, weights or format: use the newest data and the default weights, and"
                    + " say so. Call it alone.",
            returnDirect = true,
            resultConverter = TextConverter.class)
    String clarify(
            @ToolParam(description = "the question, e.g. \"Portland, Maine (PWM) or Oregon (PDX)?\"") String question) {
        return question;
    }
}
