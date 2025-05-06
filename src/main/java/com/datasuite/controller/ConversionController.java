package com.datasuite.controller;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.datasuite.dto.ContactFormDto;
import com.datasuite.service.EmailService;
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
	
	@Autowired
	private EmailService emailService;

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

            // Recursively process the root
            processNode(root, allRows, objectMapper.createObjectNode(), "");

            if (allRows.isEmpty()) {
            	return ResponseEntity.ok("No valid data to convert.");
            }

            // Extract headers dynamically
            Set<String> headers = new LinkedHashSet<>();
            allRows.forEach(row -> row.fieldNames().forEachRemaining(headers::add));

            CsvSchema.Builder schemaBuilder = CsvSchema.builder().setUseHeader(true);
            headers.forEach(schemaBuilder::addColumn);
            CsvSchema schema = schemaBuilder.build();

            // Use StringWriter instead of StringBuilder
            StringWriter writer = new StringWriter();
            csvMapper.writer(schema).writeValue(writer, allRows);

            return ResponseEntity.ok(writer.toString());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok("Error during JSON to CSV conversion");
        }
    }

    // Process node (same as before)
    private void processNode(JsonNode node, List<ObjectNode> resultRows, ObjectNode baseRow, String prefix) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String newPrefix = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                processNode(entry.getValue(), resultRows, baseRow, newPrefix);
            });
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode arrayElement = arrayNode.get(i);
                if (arrayElement.isObject()) {
                    processNode(arrayElement, resultRows, baseRow, prefix);
                } else {
                    baseRow.set(prefix, arrayElement);
                    resultRows.add(baseRow.deepCopy());
                }
            }
        } else {
            baseRow.set(prefix, node);
            resultRows.add(baseRow.deepCopy());
        }
    }

    @PostMapping("/sendEmail")
    public ResponseEntity<?> sendContactMessage(@RequestBody ContactFormDto contactForm) {
    	System.out.println("enter");
        try {
            // Validate input
            if (contactForm.getName() == null || contactForm.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Name is required");
            }
            if (contactForm.getEmail() == null || !contactForm.getEmail().contains("@")) {
                return ResponseEntity.badRequest().body("Valid email is required");
            }
            if (contactForm.getMessage() == null || contactForm.getMessage().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Message is required");
            }
            if (contactForm.getMessage().length() > 1000) {
                return ResponseEntity.badRequest().body("Message too long");
            }

            // Send email
            emailService.sendEmail(
                contactForm.getEmail(),
                "naveenorg56@gmail.com",
                "New Contact: " + (contactForm.getSubject() != null ? contactForm.getSubject() : "No Subject"),
                buildEmailContent(contactForm)
            );
            return ResponseEntity.ok().body(Map.of(
                "success", true,
                "message", "Thank you for your message!"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to send message: " + e.getMessage()
            ));
        }
    }

    private String buildEmailContent(ContactFormDto contactForm) {
        return String.format(
            "Name: %s\nEmail: %s\n\nMessage:\n%s",
            contactForm.getName(),
            contactForm.getEmail(),
            contactForm.getMessage()
        );
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