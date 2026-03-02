package edu.wisc.my.messages.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.wisc.my.messages.exception.ExpiredMessageException;
import edu.wisc.my.messages.exception.PrematureMessageException;
import edu.wisc.my.messages.exception.UserNotInMessageAudienceException;
import edu.wisc.my.messages.model.Message;
import edu.wisc.my.messages.model.User;
import edu.wisc.my.messages.service.MessagesService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mcp")
public class McpController {

  private MessagesService messagesService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  public void setMessagesService(MessagesService messagesService) {
    this.messagesService = messagesService;
  }

  @PostMapping
  public Map<String, Object> rpc(@RequestBody Map<String, Object> request) {
    Object id = request.get("id");
    String method = (String) request.get("method");
    Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", Map.of());

    try {
      switch (method) {
        case "initialize":
          return ok(id, Map.of(
            "protocolVersion", "2024-11-05",
            "serverInfo", Map.of("name", "uportal-messaging-mcp", "version", "0.1.0"),
            "capabilities", Map.of("tools", Map.of())
          ));

        case "tools/list":
          return ok(id, Map.of("tools", List.of(
            Map.of(
              "name", "messages.listFiltered",
              "description", "List messages for user groups",
              "inputSchema", Map.of(
                "type", "object",
                "properties", Map.of("groups", Map.of("type", "array", "items", Map.of("type", "string")))
              )
            ),
            Map.of(
              "name", "messages.getByIdForUser",
              "description", "Get one message by id for user groups",
              "inputSchema", Map.of(
                "type", "object",
                "required", List.of("id"),
                "properties", Map.of(
                  "id", Map.of("type", "string"),
                  "groups", Map.of("type", "array", "items", Map.of("type", "string"))
                )
              )
            ),
            Map.of(
              "name", "messages.listAllAdmin",
              "description", "List all messages without filtering",
              "inputSchema", Map.of("type", "object", "properties", Map.of())
            )
          )));

        case "tools/call":
          return handleToolCall(id, params);

        default:
          return err(id, -32601, "Method not found: " + method);
      }
    } catch (Exception e) {
      return err(id, -32000, e.getMessage());
    }
  }

  private Map<String, Object> handleToolCall(Object id, Map<String, Object> params)
    throws PrematureMessageException, ExpiredMessageException, UserNotInMessageAudienceException, JsonProcessingException {

    String name = (String) params.get("name");
    Map<String, Object> args = (Map<String, Object>) params.getOrDefault("arguments", Map.of());

    if ("messages.listFiltered".equals(name)) {
      User user = userFromArgs(args);
      List<Message> result = messagesService.filteredMessages(user);
      return toolResult(id, result);
    }

    if ("messages.getByIdForUser".equals(name)) {
      User user = userFromArgs(args);
      String messageId = (String) args.get("id");
      Message result = messagesService.messageByIdForUser(messageId, user);
      return toolResult(id, result);
    }

    if ("messages.listAllAdmin".equals(name)) {
      return toolResult(id, messagesService.allMessages());
    }

    return err(id, -32602, "Unknown tool: " + name);
  }

  private User userFromArgs(Map<String, Object> args) {
    User user = new User();
    Set<String> groups = new HashSet<>();
    Object groupsObj = args.get("groups");
    if (groupsObj instanceof List<?>) {
      for (Object g : (List<?>) groupsObj) {
        if (g != null) groups.add(g.toString());
      }
    }
    user.setGroups(groups);
    return user;
  }

  private Map<String, Object> toolResult(Object id, Object data) throws JsonProcessingException {
    return ok(id, Map.of(
      "content", List.of(Map.of(
        "type", "text",
        "text", objectMapper.writeValueAsString(data)
      ))
    ));
  }

  private Map<String, Object> ok(Object id, Object result) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("jsonrpc", "2.0");
    response.put("id", id);
    response.put("result", result);
    return response;
  }

  private Map<String, Object> err(Object id, int code, String message) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("jsonrpc", "2.0");
    response.put("id", id);
    response.put("error", Map.of("code", code, "message", message));
    return response;
  }
}