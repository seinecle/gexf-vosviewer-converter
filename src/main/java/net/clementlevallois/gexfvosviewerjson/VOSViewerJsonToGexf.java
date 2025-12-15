package net.clementlevallois.gexfvosviewerjson;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.gephi.graph.api.Column;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.exporter.plugin.ExporterGEXF;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.openide.util.Lookup;

public class VOSViewerJsonToGexf {

    private JsonObject jsonObject;
    private final String jsonVVAsString;
    
    // Gephi objects (stateful)
    private Graph graph;
    private GraphModel model;
    private Workspace workspace;
    
    // Metadata helpers
    private final Set<String> scoresKeys;
    private final Set<String> weightsKeys;
    private final Set<String> defaultNodeAttributes;
    private final Map<String, Column> mapAttributeIdToNodeAttribute;
    private final Map<String, Column> mapAttributeIdToScoreAttribute;
    private final Map<String, Column> mapAttributeIdToWeightAttribute;
    private final Set<String> attrListNodesAsString;
    
    private boolean descriptionPresent;
    private boolean clusterPresent;
    private boolean urlPresent;

    // GLOBAL LOCK: Essential because Gephi ProjectController is a singleton
    private static final Object GEPHI_LOCK = new Object();

    public VOSViewerJsonToGexf(String jsonVVAsString) {
        // Fix: Use diamond operator <> to avoid raw types
        this.attrListNodesAsString = new HashSet<>();
        this.mapAttributeIdToWeightAttribute = new HashMap<>();
        this.mapAttributeIdToScoreAttribute = new HashMap<>();
        this.mapAttributeIdToNodeAttribute = new HashMap<>();
        this.weightsKeys = new HashSet<>();
        this.scoresKeys = new HashSet<>();
        this.jsonVVAsString = jsonVVAsString;
        this.defaultNodeAttributes = Set.of("id", "label", "description", "url", "x", "y", "cluster", "weights", "scores");
    }

    public String convertToGexf() throws FileNotFoundException {
        // 1. Parsing JSON does not need the Gephi lock (Performance optimization)
        load();

        // 2. Lock EVERYTHING involving Gephi Controllers
        synchronized (GEPHI_LOCK) {
            ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
            
            // Safety check: ensure clean state
            if (pc.getCurrentProject() != null) {
                pc.closeCurrentProject();
            }
            
            pc.newProject();
            this.workspace = pc.getCurrentWorkspace();
            
            try {
                // Initialize internal model/graph references for this workspace
                this.model = Lookup.getDefault().lookup(GraphController.class).getGraphModel(workspace);
                this.graph = model.getGraph();
                
                // Metadata setup
                workspace.getProject().getProjectMetadata().setAuthor("created with nocodefunctions.com");

                // Build the graph
                getNodeAttributes();
                turnNodeJsonValuesToGexf();
                turnEdgeJsonValuesToGexf();

                // Export
                ExportController ec = Lookup.getDefault().lookup(ExportController.class);
                ExporterGEXF exporterGexf = (ExporterGEXF) ec.getExporter("gexf");
                exporterGexf.setWorkspace(workspace);
                exporterGexf.setExportDynamic(false);
                exporterGexf.setExportPosition(true);
                exporterGexf.setExportSize(true);
                exporterGexf.setExportColors(true);
                exporterGexf.setExportMeta(true);

                try (StringWriter stringWriter = new StringWriter()) {
                    ec.exportWriter(stringWriter, exporterGexf);
                    return stringWriter.toString();
                } catch (IOException e) {
                    System.err.println("Failed to export GEXF: " + e.getMessage());
                    return "";
                }

            } finally {
                // Always close the project we created to free memory and reset state
                if (pc.getCurrentProject() != null) {
                    pc.closeCurrentProject();
                }
            }
        }
    }

    private void load() {
        // Helper: parsing string to JsonObject
        try (JsonReader jsonReader = Json.createReader(new StringReader(jsonVVAsString))) {
            jsonObject = jsonReader.readObject();
        }
    }

    // ... [The rest of your helper methods: getNodeAttributes, turnNodeJson... remain mostly the same] ...
    // Note: ensure they use the 'model' and 'graph' instance variables initialized in the synchronized block.
    
    private void getNodeAttributes() {
        JsonObject network = jsonObject.getJsonObject("network");
        if (network == null) return;
        
        JsonArray items = network.getJsonArray("items");
        if (items == null) return;

        Iterator<JsonValue> iteratorItems = items.iterator();
        while (iteratorItems.hasNext()) {
            JsonObject item = (JsonObject) iteratorItems.next();
            for (String key : item.keySet()) {
                if (key.equals("id") || key.equals("label")) continue;

                if (!descriptionPresent && key.equals("description")) {
                    descriptionPresent = true;
                    Column attribute = model.getNodeTable().addColumn("description", String.class);
                    attrListNodesAsString.add("description");
                    mapAttributeIdToNodeAttribute.put(key, attribute);
                } else if (!urlPresent && key.equals("url")) {
                    urlPresent = true;
                    Column attribute = model.getNodeTable().addColumn("url", String.class);
                    attrListNodesAsString.add("url");
                    mapAttributeIdToNodeAttribute.put(key, attribute);
                } else if (!clusterPresent && key.equals("cluster")) {
                    clusterPresent = true;
                    Column attribute = model.getNodeTable().addColumn("cluster", Integer.class);
                    mapAttributeIdToNodeAttribute.put(key, attribute);
                } else if (key.equals("scores")) {
                    JsonObject scores = item.getJsonObject("scores");
                    scoresKeys.addAll(scores.keySet());
                } else if (key.equals("weights")) {
                    JsonObject weights = item.getJsonObject("weights");
                    weightsKeys.addAll(weights.keySet());
                } else if (!defaultNodeAttributes.contains(key) && !attrListNodesAsString.contains(key)) {
                    JsonValue value = item.get(key);
                    if (value == null) continue;

                    String originalKey = key;
                    if (key.equals("Id")) key = "id-attribute-from-gexf";
                    else if (key.equals("Label")) key = "label-attribute-from-gexf";

                    if (attrListNodesAsString.contains(key)) continue;

                    // Switch expression is fine (Java 14+)
                    Column attribute = switch (value.getValueType()) {
                        case NUMBER -> model.getNodeTable().addColumn(key, Double.class);
                        case STRING -> model.getNodeTable().addColumn(key, String.class);
                        case TRUE, FALSE -> model.getNodeTable().addColumn(key, Boolean.class);
                        default -> model.getNodeTable().addColumn(key, Object.class);
                    };
                    
                    attrListNodesAsString.add(key);
                    mapAttributeIdToNodeAttribute.put(originalKey, attribute);
                }
            }
        }

        for (String key : scoresKeys) {
            Column attribute = model.getNodeTable().addColumn(key, Double.class);
            mapAttributeIdToScoreAttribute.put(key, attribute);
        }
        for (String key : weightsKeys) {
            Column attribute = model.getNodeTable().addColumn(key, Double.class);
            mapAttributeIdToWeightAttribute.put(key, attribute);
        }
    }

    private void turnNodeJsonValuesToGexf() {
        JsonObject network = jsonObject.getJsonObject("network");
        if (network == null) return;
        JsonArray items = network.getJsonArray("items");
        if (items == null) return;

        Iterator<JsonValue> iteratorItems = items.iterator();
        int counter = 0;
        
        while (iteratorItems.hasNext()) {
            JsonObject item = (JsonObject) iteratorItems.next();
            Set<String> keySet = item.keySet();
            
            // Robust ID handling
            String idString = "";
            if (item.containsKey("id")) {
                JsonValue idVal = item.get("id");
                if (idVal.getValueType() == JsonValue.ValueType.NUMBER) {
                    idString = item.getJsonNumber("id").toString();
                } else if (idVal.getValueType() == JsonValue.ValueType.STRING) {
                    idString = item.getString("id");
                }
            }

            Node nodeGexf = model.factory().newNode(idString);
            if (keySet.contains("x") && keySet.contains("y")) {
                nodeGexf.setZ(0.0f);
            }

            for (String key : keySet) {
                // Mapping logic...
                String lookupKey = key;
                if (key.equals("Id")) lookupKey = "id-attribute-from-gexf";
                else if (key.equals("Label")) lookupKey = "label-attribute-from-gexf";

                switch (key) {
                    case "label" -> {
                        if (item.isNull(key)) {
                            nodeGexf.setLabel(key + " was null - " + counter++);
                        } else {
                            nodeGexf.setLabel(item.getString(key));
                        }
                    }
                    case "description", "url", "cluster" -> {
                        Column att = mapAttributeIdToNodeAttribute.get(key);
                        if (att != null) {
                            if (item.isNull(key)) {
                                // Default for cluster is usually 0 or null
                                nodeGexf.setAttribute(att, key.equals("cluster") ? 0 : "null");
                            } else {
                                if (key.equals("cluster")) nodeGexf.setAttribute(att, item.getInt(key));
                                else nodeGexf.setAttribute(att, item.getString(key));
                            }
                        }
                    }
                    case "scores" -> {
                        JsonObject scoresObject = item.getJsonObject(key);
                        for (String keyScore : scoresObject.keySet()) {
                            Column att = mapAttributeIdToScoreAttribute.get(keyScore);
                            if (att != null) {
                                double val = scoresObject.isNull(keyScore) ? 0d : scoresObject.getJsonNumber(keyScore).doubleValue();
                                nodeGexf.setAttribute(att, val);
                            }
                        }
                    }
                    case "weights" -> {
                        JsonObject weightsObject = item.getJsonObject(key);
                        for (String keyWeight : weightsObject.keySet()) {
                            Column att = mapAttributeIdToWeightAttribute.get(keyWeight);
                            if (att != null) {
                                double val = weightsObject.isNull(keyWeight) ? 0d : weightsObject.getJsonNumber(keyWeight).doubleValue();
                                nodeGexf.setAttribute(att, val);
                            }
                        }
                    }
                    case "x" -> nodeGexf.setX(item.getJsonNumber("x").bigDecimalValue().floatValue());
                    case "y" -> nodeGexf.setY(item.getJsonNumber("y").bigDecimalValue().floatValue());
                    case "id" -> {} // handled at creation
                    default -> {
                        // General Attribute Handling
                        Column att = mapAttributeIdToNodeAttribute.get(lookupKey);
                        if (att != null && !item.isNull(key)) {
                            // Helper to set attribute based on type
                            setNodeAttributeSafe(nodeGexf, att, item, key);
                        }
                    }
                }
            }
            graph.addNode(nodeGexf);
        }
    }

    private void turnEdgeJsonValuesToGexf() {
        JsonObject network = jsonObject.getJsonObject("network");
        if (network == null) return;
        JsonArray links = network.getJsonArray("links");
        if (links == null) return;

        for (JsonValue val : links) {
            JsonObject link = (JsonObject) val;
            String sourceId = getStringOrNumber(link, "source_id");
            String targetId = getStringOrNumber(link, "target_id");

            Node node1 = graph.getNode(sourceId);
            Node node2 = graph.getNode(targetId);

            if (node1 != null && node2 != null) {
                Edge edge = model.factory().newEdge(node1, node2, false);
                if (link.containsKey("strength") && !link.isNull("strength")) {
                    edge.setWeight(link.getJsonNumber("strength").bigDecimalValue().floatValue());
                }
                graph.addEdge(edge);
            }
        }
    }

    // Helper to reduce code duplication in ID retrieval
    private String getStringOrNumber(JsonObject obj, String key) {
        if (!obj.containsKey(key) || obj.isNull(key)) return "";
        JsonValue val = obj.get(key);
        return val.getValueType() == JsonValue.ValueType.NUMBER 
               ? obj.getJsonNumber(key).toString() 
               : obj.getString(key);
    }
    
    // Helper to switch on attribute type cleanly
    private void setNodeAttributeSafe(Node node, Column att, JsonObject item, String key) {
        Class<?> type = att.getTypeClass();
        if (type == String.class) node.setAttribute(att, item.getString(key));
        else if (type == Double.class) node.setAttribute(att, item.getJsonNumber(key).doubleValue());
        else if (type == Integer.class) node.setAttribute(att, item.getJsonNumber(key).intValue());
        else if (type == Long.class) node.setAttribute(att, item.getJsonNumber(key).longValue());
        else if (type == Boolean.class) node.setAttribute(att, item.getBoolean(key));
    }
}