/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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
import java.nio.file.Path;
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

/**
 *
 * @author LEVALLOIS
 */
public class VOSViewerJsonToGexf {

    private JsonObject jsonObject;
    private final String jsonVVAsString;
    private Graph graph;
    private GraphModel model;
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
    private ProjectController pc;
    private Workspace workspace;
    private static final Object LOCK = new Object();

    public VOSViewerJsonToGexf(String jsonVVAsString) {
        this.attrListNodesAsString = new HashSet();
        this.mapAttributeIdToWeightAttribute = new HashMap();
        this.mapAttributeIdToScoreAttribute = new HashMap();
        this.mapAttributeIdToNodeAttribute = new HashMap();
        this.weightsKeys = new HashSet();
        this.scoresKeys = new HashSet();
        this.jsonVVAsString = jsonVVAsString;
        this.defaultNodeAttributes = Set.of("id", "label", "description", "url", "x", "y", "cluster", "weights", "scores");
    }

    public String convertToGexf() throws FileNotFoundException {
        try {
            load();
            gexfInitiate();
            getNodeAttributes();
            turnNodeJsonValuesToGexf();
            turnEdgeJsonValuesToGexf();
            synchronized (LOCK) {
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
                    System.out.println("Failed to export GEXF");
                    return "";
                }
            }
        } finally {
            if (workspace != null) {
                pc.closeCurrentWorkspace();
                pc.closeCurrentProject();
            }
        }
    }

    private void load() throws FileNotFoundException {
        JsonReader jsonReader = Json.createReader(new StringReader(jsonVVAsString));
        jsonObject = jsonReader.readObject();
    }

    private void gexfInitiate() {
        pc = Lookup.getDefault().lookup(ProjectController.class);
        workspace = pc.newWorkspace(pc.newProject());
        pc.openWorkspace(workspace);  // Open this specific workspace
        workspace.getProject().getProjectMetadata().setAuthor("created with nocodefunctions.com");

        // Get graph model for THIS workspace specifically
        model = Lookup.getDefault().lookup(GraphController.class).getGraphModel(workspace);
        graph = model.getGraph();
    }

    private void getNodeAttributes() {

        JsonObject network = jsonObject.getJsonObject("network");
        if (network == null) {
            return;
        }
        JsonArray items = network.getJsonArray("items");
        if (items == null) {
            return;
        }
        Iterator<JsonValue> iteratorItems = items.iterator();
        while (iteratorItems.hasNext()) {
            JsonObject item = (JsonObject) iteratorItems.next();
            for (String key : item.keySet()) {

                // these are treated at the step of node creation / we don't record them as attributes
                if (key.equals("id") || key.equals("label")) {
                    continue;
                }
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
                    for (String scoreKey : scores.keySet()) {
                        scoresKeys.add(scoreKey);
                    }
                } else if (key.equals("weights")) {
                    JsonObject weights = item.getJsonObject("weights");
                    for (String weightKey : weights.keySet()) {
                        weightsKeys.add(weightKey);
                    }
                } else if (!defaultNodeAttributes.contains(key) && !attrListNodesAsString.contains(key)) {
                    JsonValue value = item.get(key);
                    if (value == null) {
                        continue;
                    }
                    if (key.equals("Id")) {
                        key = "id-attribute-from-gexf";
                    } else if (key.equals("Label")) {
                        key = "label-attribute-from-gexf";
                    }

                    if (attrListNodesAsString.contains(key)) {
                        continue;
                    }

                    JsonValue.ValueType valueType = value.getValueType();
                    Column attribute;
                    switch (valueType) {
                        case NUMBER -> {
                            attribute = model.getNodeTable().addColumn(key, Double.class);
                        }
                        case STRING -> {
                            attribute = model.getNodeTable().addColumn(key, String.class);
                        }
                        case TRUE -> {
                            attribute = model.getNodeTable().addColumn(key, Boolean.class);
                        }
                        case FALSE -> {
                            attribute = model.getNodeTable().addColumn(key, Boolean.class);
                        }
                        default -> {
                            attribute = model.getNodeTable().addColumn(key, Object.class);
                        }
                    }
                    attrListNodesAsString.add(key);
                    mapAttributeIdToNodeAttribute.put(key, attribute);
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
        if (network == null) {
            return;
        }
        JsonArray items = network.getJsonArray("items");
        if (items == null) {
            return;
        }

        Iterator<JsonValue> iteratorItems = items.iterator();
        Set<String> keySet;
        while (iteratorItems.hasNext()) {
            JsonObject item = (JsonObject) iteratorItems.next();
            keySet = item.keySet();
            JsonValue valueTypeUnknown = item.get("id");
            String idString = "";
            switch (valueTypeUnknown.getValueType()) {
                case NUMBER ->
                    idString = item.getJsonNumber("id").toString();
                case STRING ->
                    idString = item.getString("id");
                case ARRAY -> {
                }
                case OBJECT -> {
                }
                case TRUE -> {
                }
                case FALSE -> {
                }
                case NULL -> {
                }
                default -> {
                }
            }

            Node nodeGexf = model.factory().newNode(idString);
            if (keySet.contains("x") && keySet.contains("y")) {
                nodeGexf.setZ(0.0f);
            }
            int counter = 0;
            for (String key : keySet) {

                if (key.equals("Id")) {
                    key = "id-attribute-from-gexf";
                } else if (key.equals("Label")) {
                    key = "label-attribute-from-gexf";
                }

                switch (key) {
                    case "label" -> {
                        if (item.isNull(key)) {
                            nodeGexf.setLabel(key + " was null in json file - " + counter++);
                        } else {
                            nodeGexf.setLabel(item.getString(key));
                        }
                    }
                    case "description", "url" -> {
                        Column att = mapAttributeIdToNodeAttribute.get(key);
                        if (item.isNull(key)) {
                            nodeGexf.setAttribute(att, key + " was null in json file");
                        } else {
                            nodeGexf.setAttribute(att, item.getString(key));
                        }
                    }
                    case "cluster" -> {
                        Column att = mapAttributeIdToNodeAttribute.get(key);
                        if (item.isNull(key)) {
                            nodeGexf.setAttribute(att, 0);
                        } else {
                            nodeGexf.setAttribute(att, item.getInt(key));
                        }
                    }
                    case "scores" -> {
                        JsonObject scoresObject = item.getJsonObject(key);
                        for (String keyScore : scoresObject.keySet()) {
                            Column att = mapAttributeIdToScoreAttribute.get(keyScore);
                            if (scoresObject.isNull(keyScore)) {
                                nodeGexf.setAttribute(att, 0d);
                            } else {
                                nodeGexf.setAttribute(att, scoresObject.getJsonNumber(keyScore).doubleValue());
                            }
                        }
                    }
                    case "weights" -> {
                        JsonObject weightsObject = item.getJsonObject(key);
                        for (String keyWeight : weightsObject.keySet()) {
                            Column att = mapAttributeIdToWeightAttribute.get(keyWeight);
                            if (weightsObject.isNull(keyWeight)) {
                                nodeGexf.setAttribute(att, 0d);
                            } else {
                                nodeGexf.setAttribute(att, weightsObject.getJsonNumber(keyWeight).doubleValue());
                            }
                        }
                    }
                    case "x" ->
                        nodeGexf.setX(item.getJsonNumber("x").bigDecimalValue().floatValue());
                    case "y" ->
                        nodeGexf.setY(item.getJsonNumber("y").bigDecimalValue().floatValue());
                    case "id" -> {
                    }
                    case "id-attribute-from-gexf" -> {
                        Column att = mapAttributeIdToNodeAttribute.get(key);
                        if (att.getTypeClass() == String.class) {
                            nodeGexf.setAttribute(att, item.getString("Id"));
                        } else if (att.getTypeClass() == Double.class || att.getTypeClass() == Float.class) {
                            nodeGexf.setAttribute(att, item.getJsonNumber("Id").doubleValue());
                        } else if (att.getTypeClass() == Integer.class) {
                            nodeGexf.setAttribute(att, item.getJsonNumber("Id").intValue());
                        } else if (att.getTypeClass() == Long.class) {
                            nodeGexf.setAttribute(att, item.getJsonNumber("Id").longValue());
                        } else if (att.getTypeClass() == Boolean.class) {
                            nodeGexf.setAttribute(att, item.getBoolean("Id"));
                        }
                    }
                    case "label-attribute-from-gexf" -> {
                        Column att = mapAttributeIdToNodeAttribute.get(key);
                        if (att.getTypeClass() == String.class) {
                            nodeGexf.setAttribute(att, item.getString("Label"));
                        } else if (att.getTypeClass() == Double.class || att.getTypeClass() == Float.class) {
                            nodeGexf.setAttribute(att, item.getJsonNumber("Label").doubleValue());
                        } else if (att.getTypeClass() == Integer.class) {
                            nodeGexf.setAttribute(att, item.getJsonNumber("Label").intValue());
                        } else if (att.getTypeClass() == Long.class) {
                            nodeGexf.setAttribute(att, item.getJsonNumber("Label").longValue());
                        } else if (att.getTypeClass() == Boolean.class) {
                            nodeGexf.setAttribute(att, item.getBoolean("Label"));
                        }
                    }
                    default -> {
                        Column att = mapAttributeIdToNodeAttribute.get(key);
                        if (att.getTypeClass() == String.class) {
                            nodeGexf.setAttribute(att, item.getString(key));
                        } else if (att.getTypeClass() == Double.class || att.getTypeClass() == Float.class) {
                            nodeGexf.setAttribute(att, item.getJsonNumber(key).doubleValue());
                        } else if (att.getTypeClass() == Integer.class) {
                            nodeGexf.setAttribute(att, item.getJsonNumber(key).intValue());
                        } else if (att.getTypeClass() == Long.class) {
                            nodeGexf.setAttribute(att, item.getJsonNumber(key).longValue());
                        } else if (att.getTypeClass() == Boolean.class) {
                            nodeGexf.setAttribute(att, item.getBoolean(key));
                        }
                    }
                }
            }
            graph.addNode(nodeGexf);
        }
    }

    private void turnEdgeJsonValuesToGexf() {
        JsonObject network = jsonObject.getJsonObject("network");
        if (network == null) {
            return;
        }
        JsonArray links = network.getJsonArray("links");
        if (links == null) {
            return;
        }

        Iterator<JsonValue> iteratorLinks = links.iterator();
        while (iteratorLinks.hasNext()) {
            JsonObject link = (JsonObject) iteratorLinks.next();
            JsonValue sourceIdValueTypeUnknown = link.get("source_id");
            String sourceIdString = "";
            switch (sourceIdValueTypeUnknown.getValueType()) {
                case NUMBER:
                    sourceIdString = link.getJsonNumber("source_id").toString();
                    break;
                case STRING:
                    sourceIdString = link.getString("source_id");
                    break;
                default:
            }

            JsonValue targetIdValueTypeUnknown = link.get("target_id");
            String targetIdString = "";
            switch (targetIdValueTypeUnknown.getValueType()) {
                case NUMBER:
                    targetIdString = link.getJsonNumber("target_id").toString();
                    break;
                case STRING:
                    targetIdString = link.getString("target_id");
                    break;
                default:
            }

            Node node1 = graph.getNode(sourceIdString);
            Node node2 = graph.getNode(targetIdString);
            if (node1 != null && node2 != null) {
                Edge edge = model.factory().newEdge(node1, node2, false);
                edge.setWeight(link.getJsonNumber("strength").bigDecimalValue().floatValue());
                graph.addEdge(edge);
            } else {
                System.out.println("node 1 or node 2 was null when trying to create edge");
            }
        }
    }
}
