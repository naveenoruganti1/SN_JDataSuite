package com.datasuite.controller;

import java.io.StringWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
            return ResponseEntity.badRequest().body("Error processing JSON to XML: " + e.getMessage());
        }
    }

    @PostMapping(value = "/json_to_yaml", consumes = MediaType.APPLICATION_JSON_VALUE, produces = "application/x-yaml")
    public ResponseEntity<String> convertJsonToYaml(@RequestBody String jsonInput) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonInput);
            return ResponseEntity.ok(yamlMapper.writeValueAsString(jsonNode));
        } catch (Exception e) {
            logger.error("Error processing JSON to YAML", e);
            return ResponseEntity.badRequest().body("Error processing JSON to YAML: " + e.getMessage());
        }
    }

    @PostMapping(value = "/json_to_csv", consumes = MediaType.APPLICATION_JSON_VALUE, produces = "text/csv")
    public ResponseEntity<String> convertJsonToCsv(@RequestBody String jsonInput) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonInput);
            while (jsonNode.isObject() && jsonNode.elements().hasNext()) {
                jsonNode = jsonNode.elements().next();
            }
            if (!jsonNode.isArray() || jsonNode.size() == 0) {
                return ResponseEntity.badRequest().body("Error: JSON must be an array of objects for CSV conversion.");
            }
            ArrayNode flattenedArray = objectMapper.createArrayNode();
            for (JsonNode node : jsonNode) {
                flattenedArray.add(flattenJson(node, ""));
            }
            CsvSchema.Builder schemaBuilder = CsvSchema.builder();
            JsonNode firstNode = flattenedArray.get(0);
            firstNode.fieldNames().forEachRemaining(schemaBuilder::addColumn);
            CsvSchema schema = schemaBuilder.setUseHeader(true).build();
            StringWriter stringWriter = new StringWriter();
            csvMapper.writer(schema).writeValue(stringWriter, flattenedArray);
            return ResponseEntity.ok(stringWriter.toString());
        } catch (Exception e) {
            logger.error("Error processing JSON to CSV", e);
            return ResponseEntity.badRequest().body("Error processing JSON to CSV: " + e.getMessage());
        }
    }

    private JsonNode flattenJson(JsonNode node, String parentKey) {
        ObjectNode flatNode = objectMapper.createObjectNode();
        node.fields().forEachRemaining(entry -> {
            String newKey = parentKey.isEmpty() ? entry.getKey() : parentKey + "." + entry.getKey();
            if (entry.getValue().isObject()) {
                flatNode.setAll((ObjectNode) flattenJson(entry.getValue(), newKey));
            } else {
                flatNode.set(newKey, entry.getValue());
            }
        });
        return flatNode;
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