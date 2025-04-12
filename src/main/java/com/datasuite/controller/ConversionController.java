package com.datasuite.controller;

import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

@RestController
@RequestMapping("/v1/convert")
public class ConversionController {

    private static final Logger logger = LoggerFactory.getLogger(ConversionController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final XmlMapper xmlMapper = new XmlMapper();
    private final YAMLMapper yamlMapper = new YAMLMapper();
    private final CsvMapper csvMapper = new CsvMapper();

    @PostMapping(value = "/json_to_xml", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> convertJsonToXml(@RequestBody String jsonInput) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonInput);
            if (jsonNode.isArray()) {
                ObjectNode rootNode = objectMapper.createObjectNode();
                rootNode.set("root", jsonNode);
                jsonNode = rootNode;
            }
            return ResponseEntity.ok(xmlMapper.writeValueAsString(jsonNode));
        } catch (Exception e) {
            logger.error("Error processing JSON to XML", e);
            return ResponseEntity.ok("Error processing JSON to XML");
            //return ResponseEntity.badRequest().body("Error processing JSON to XML: " + e.getMessage());
        }
    }

    @PostMapping(value = "/json_to_yaml", consumes = MediaType.APPLICATION_JSON_VALUE, produces = "application/x-yaml")
    public ResponseEntity<String> convertJsonToYaml(@RequestBody String jsonInput) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonInput);
            return ResponseEntity.ok(yamlMapper.writeValueAsString(jsonNode));
        } catch (Exception e) {
            logger.error("Error processing JSON to YAML", e);
            return ResponseEntity.ok("Error processing JSON to YAML");
            //return ResponseEntity.badRequest().body("Error processing JSON to YAML: " + e.getMessage());
        }
    }

    @PostMapping(value = "/json_to_csv", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertJsonToCsv(@RequestBody String jsonInput) {
        try {
            JsonNode root = objectMapper.readTree(jsonInput);
            List<ObjectNode> allRows = new ArrayList<>();

            List<JsonNode> records = extractRecords(root);
            for (JsonNode record : records) {
                if (!record.isObject()) continue;
                processNode((ObjectNode) record, allRows, objectMapper.createObjectNode());
            }

            if (allRows.isEmpty()) {
                return ResponseEntity.badRequest().body("No valid data to convert.");
            }

            // Extract headers dynamically
            Set<String> headers = new LinkedHashSet<>();
            allRows.forEach(row -> row.fieldNames().forEachRemaining(headers::add));

            CsvSchema.Builder schemaBuilder = CsvSchema.builder().setUseHeader(true);
            headers.forEach(schemaBuilder::addColumn);
            CsvSchema schema = schemaBuilder.build();

            StringWriter writer = new StringWriter();
            csvMapper.writer(schema).writeValue(writer, allRows);
            return ResponseEntity.ok(writer.toString());

        } catch (Exception e) {
            logger.error("Error during JSON to CSV conversion", e);
            logger.error("Error processing JSON to YAML", e);
            return ResponseEntity.ok("Error processing JSON to YAML");
        }
    }

    private List<JsonNode> extractRecords(JsonNode root) {
        List<JsonNode> records = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(records::add);
        } else if (root.isObject()) {
            // Traverse deeply to find arrays of objects
            Deque<JsonNode> stack = new ArrayDeque<>();
            stack.push(root);
            while (!stack.isEmpty()) {
                JsonNode node = stack.pop();
                if (node.isArray() && node.size() > 0 && node.get(0).isObject()) {
                    node.forEach(records::add);
                } else if (node.isObject()) {
                    node.elements().forEachRemaining(stack::push);
                }
            }
        }
        return records;
    }

    private void processNode(ObjectNode inputNode, List<ObjectNode> resultRows, ObjectNode baseRow) {
        Map<String, JsonNode> arrayFields = new LinkedHashMap<>();

        inputNode.fields().forEachRemaining(entry -> {
            if (entry.getValue().isArray() && entry.getValue().size() > 0 &&
                    entry.getValue().get(0).isObject()) {
                arrayFields.put(entry.getKey(), entry.getValue());
            } else if (!entry.getValue().isObject()) {
                baseRow.set(entry.getKey(), entry.getValue());
            }
        });

        if (arrayFields.isEmpty()) {
            resultRows.add(baseRow.deepCopy());
        } else {
            // Explode one array at a time
            for (Map.Entry<String, JsonNode> entry : arrayFields.entrySet()) {
                String arrayName = entry.getKey();
                for (JsonNode item : entry.getValue()) {
                    if (!item.isObject()) continue;
                    ObjectNode row = baseRow.deepCopy();
                    item.fields().forEachRemaining(nestedField -> 
                        row.set(arrayName + "." + nestedField.getKey(), nestedField.getValue()));
                    resultRows.add(row);
                }
            }
        }
    }
}

@ControllerAdvice
class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllExceptions(Exception ex) {
        logger.error("Unhandled error: ", ex);
        ObjectNode errorResponse = new ObjectMapper().createObjectNode();
        errorResponse.put("error", "Internal Server Error");
        errorResponse.put("message", ex.getMessage());
        return ResponseEntity.status(500).body(errorResponse);
    }
}