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
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static java.util.stream.Collectors.toList;
import net.clementlevallois.utils.UnicodeBOMInputStream;
import org.gephi.graph.api.Column;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 * @author LEVALLOIS
 */
public class VOSViewerJsonToGexf {

    /**
     * @param args the command line arguments
     */
    Path filePath;
    InputStream is;
    JsonObject jsonObject;
    String gexf;
    Graph graph;
    GraphModel model;
    Set<String> scoresKeys = new HashSet();
    Set<String> weightsKeys = new HashSet();
    Set<String> stringAttributes = new HashSet();
    Set<String> defaultNodeAttributes;
    Set<Column> attrListNodes = new HashSet();
    Map<String, Column> mapAttributeIdToNodeAttribute = new HashMap();
    Map<String, Column> mapAttributeIdToScoreAttribute = new HashMap();
    Map<String, Column> mapAttributeIdToWeightAttribute = new HashMap();
    Set<String> attrListNodesAsString = new HashSet();
    boolean descriptionPresent;
    boolean clusterPresent;
    boolean urlPresent;

    public VOSViewerJsonToGexf(Path filePath) {
        this.filePath = filePath;
    }

    public VOSViewerJsonToGexf(InputStream is) {
        this.is = is;
    }

    public String convertToGexf() throws FileNotFoundException {
        defaultNodeAttributes = Set.of("id", "label", "description", "url", "x", "y", "cluster", "weights", "scores");
        load();
        gexfInitiate();
        getNodeAttributes();
        turnNodeJsonValuesToGexf();
        turnEdgeJsonValuesToGexf();

        return gexf;
    }

    private void load() throws FileNotFoundException {
        try {
            JsonReader jsonReader = null;
            UnicodeBOMInputStream ubis = null;
            if (is == null) {
                try {
                    FileInputStream fis = new FileInputStream(filePath.toString());
                    ubis = new UnicodeBOMInputStream(fis);
                } catch (NullPointerException | IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            } else {
                try {
                    ubis = new UnicodeBOMInputStream(is);
                } catch (NullPointerException | IOException ex) {
                    Exceptions.printStackTrace(ex);
                }

            }
            InputStreamReader isr = new InputStreamReader(ubis);
            BufferedReader br = new BufferedReader(isr);

            ubis.skipBOM();

            List<String> lines = br.lines().collect(toList());

            String jsonFixed = String.join("\n", lines).replace("\"\"", "\\\"");
//            System.out.println("jsonFixed: " + jsonFixed);
            jsonReader = Json.createReader(new StringReader(jsonFixed));
            jsonObject = jsonReader.readObject();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private void gexfInitiate() {
        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();
        Workspace workspace = pc.getCurrentWorkspace();
        model = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
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
            Column stringAttribute;
            for (String key : item.keySet()) {
                if (!descriptionPresent && key.equals("description")) {
                    descriptionPresent = true;
                    Column attribute = model.getNodeTable().addColumn("description", String.class);
                    attrListNodesAsString.add("description");
                    mapAttributeIdToNodeAttribute.put(key, attribute);
                }

                if (!urlPresent && key.equals("url")) {
                    urlPresent = true;
                    Column attribute = model.getNodeTable().addColumn("url", String.class);
                    attrListNodesAsString.add("url");
                    mapAttributeIdToNodeAttribute.put(key, attribute);

                }
                if (!clusterPresent && key.equals("cluster")) {
                    clusterPresent = true;
                    Column attribute = model.getNodeTable().addColumn("cluster", Integer.class);
                    mapAttributeIdToNodeAttribute.put(key, attribute);
                }
                if (!defaultNodeAttributes.contains(key) && !attrListNodesAsString.contains(key)) {
                    Column attribute = model.getNodeTable().addColumn(key, String.class);
                    attrListNodesAsString.add(key);
                    mapAttributeIdToNodeAttribute.put(key, attribute);
                }
                if (key.equals("scores")) {
                    JsonObject scores = item.getJsonObject("scores");
                    for (String scoreKey : scores.keySet()) {
                        scoresKeys.add(scoreKey);
                    }
                }
                if (key.equals("weights")) {
                    JsonObject weights = item.getJsonObject("weights");
                    for (String weightKey : weights.keySet()) {
                        weightsKeys.add(weightKey);
                    }
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
                case NUMBER:
                    idString = item.getJsonNumber("id").toString();
                    break;
                case STRING:
                    idString = item.getString("id");
                    break;
                default:
            }

            Node nodeGexf = model.factory().newNode(idString);
            if (keySet.contains("label")) {
                nodeGexf.setLabel(item.getString("label"));
            }
            if (keySet.contains("description")) {
                Column att = mapAttributeIdToNodeAttribute.get("description");
                if (item.isNull("description")) {
                    nodeGexf.setAttribute(att, "");
                } else {
                    nodeGexf.setAttribute(att, item.getString("description"));
                }
            }

            if (keySet.contains("url")) {
                Column att = mapAttributeIdToNodeAttribute.get("url");
                if (item.isNull("url")) {
                    nodeGexf.setAttribute(att, "");
                } else {
                    nodeGexf.setAttribute(att, item.getString("url"));
                }
            }

            if (keySet.contains("x")) {
                nodeGexf.setX(item.getJsonNumber("x").bigDecimalValue().floatValue());
            }
            if (keySet.contains("y")) {
                nodeGexf.setY(item.getJsonNumber("y").bigDecimalValue().floatValue());
            }
            if (keySet.contains("x") && keySet.contains("y")) {
                nodeGexf.setZ(0.0f);
            }

            if (keySet.contains("cluster")) {
                Column att = mapAttributeIdToNodeAttribute.get("cluster");
                if (item.isNull("cluster")) {
                    nodeGexf.setAttribute(att, 0);
                } else {
                    nodeGexf.setAttribute(att, item.getInt("cluster"));
                }
            }

            if (keySet.contains("scores")) {
                JsonObject scoresObject = item.getJsonObject("scores");
                for (String key : scoresObject.keySet()) {
                    Column att = mapAttributeIdToScoreAttribute.get(key);
                    if (scoresObject.isNull(key)) {
                        nodeGexf.setAttribute(att, 0d);
                    } else {
                        nodeGexf.setAttribute(att, scoresObject.getJsonNumber(key).doubleValue());
                    }
                }
            }

            if (keySet.contains("weights")) {
                JsonObject weightsObject = item.getJsonObject("weights");
                for (String key : weightsObject.keySet()) {
                    Column att = mapAttributeIdToWeightAttribute.get(key);
                    if (weightsObject.isNull(key)) {
                        nodeGexf.setAttribute(att, 0d);
                    } else {
                        nodeGexf.setAttribute(att, weightsObject.getJsonNumber(key).doubleValue());
                    }
                }
            }
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
            Edge edge = model.factory().newEdge(node1, node2, false);
            edge.setWeight(link.getJsonNumber("strength").bigDecimalValue().floatValue());
        }
    }
}
