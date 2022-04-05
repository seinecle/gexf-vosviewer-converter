/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.gexfvosviewerjson;

import it.uniroma1.dis.wsngroup.gexf4j.core.Edge;
import it.uniroma1.dis.wsngroup.gexf4j.core.EdgeType;
import it.uniroma1.dis.wsngroup.gexf4j.core.Gexf;
import it.uniroma1.dis.wsngroup.gexf4j.core.Graph;
import it.uniroma1.dis.wsngroup.gexf4j.core.Mode;
import it.uniroma1.dis.wsngroup.gexf4j.core.Node;
import it.uniroma1.dis.wsngroup.gexf4j.core.data.AttributeClass;
import it.uniroma1.dis.wsngroup.gexf4j.core.data.AttributeList;
import it.uniroma1.dis.wsngroup.gexf4j.core.data.AttributeType;
import it.uniroma1.dis.wsngroup.gexf4j.core.data.AttributeValue;
import it.uniroma1.dis.wsngroup.gexf4j.core.impl.GexfImpl;
import it.uniroma1.dis.wsngroup.gexf4j.core.impl.data.AttributeImpl;
import it.uniroma1.dis.wsngroup.gexf4j.core.impl.data.AttributeListImpl;
import it.uniroma1.dis.wsngroup.gexf4j.core.impl.data.AttributeValueImpl;
import it.uniroma1.dis.wsngroup.gexf4j.core.impl.viz.PositionImpl;
import it.uniroma1.dis.wsngroup.gexf4j.core.viz.Position;
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
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import net.clementlevallois.utils.UnicodeBOMInputStream;
import org.openide.util.Exceptions;

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
    Gexf gexf;
    Graph graph;
    Set<String> scoresKeys = new HashSet();
    Set<String> weightsKeys = new HashSet();
    Set<String> stringAttributes = new HashSet();
    Set<String> defaultNodeAttributes;
    AttributeList attrListNodes;
    Map<String, AttributeImpl> mapAttributeIdToNodeAttribute = new HashMap();
    Map<String, AttributeImpl> mapAttributeIdToScoreAttribute = new HashMap();
    Map<String, AttributeImpl> mapAttributeIdToWeightAttribute = new HashMap();
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

    public Gexf convertToGexf() throws FileNotFoundException {
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
            System.out.println("jsonFixed: " + jsonFixed);
            jsonReader = Json.createReader(new StringReader(jsonFixed));
            jsonObject = jsonReader.readObject();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

    }

    private void gexfInitiate() {
        gexf = new GexfImpl();
        Calendar date = Calendar.getInstance();

        gexf.getMetadata()
                .setLastModified(date.getTime())
                .setCreator("")
                .setDescription("");
        gexf.setVisualization(true);

        graph = gexf.getGraph();
        graph.setDefaultEdgeType(EdgeType.UNDIRECTED).setMode(Mode.STATIC);

        attrListNodes = new AttributeListImpl(AttributeClass.NODE);

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
            AttributeImpl stringAttribute;
            for (String key : item.keySet()) {
                if (!descriptionPresent && key.equals("description")) {
                    descriptionPresent = true;
                    AttributeImpl descriptionAttribute = new AttributeImpl("description", AttributeType.STRING, "description");
                    attrListNodes.add(descriptionAttribute);
                    mapAttributeIdToNodeAttribute.put(key, descriptionAttribute);
                }

                if (!urlPresent && key.equals("url")) {
                    urlPresent = true;
                    AttributeImpl urlAttribute = new AttributeImpl("url", AttributeType.STRING, "url");
                    attrListNodes.add(urlAttribute);
                    mapAttributeIdToNodeAttribute.put(key, urlAttribute);

                }
                if (!clusterPresent && key.equals("cluster")) {
                    clusterPresent = true;
                    AttributeImpl clusterAttribute = new AttributeImpl("cluster", AttributeType.INTEGER, "cluster");
                    attrListNodes.add(clusterAttribute);
                    mapAttributeIdToNodeAttribute.put(key, clusterAttribute);

                }
                if (!defaultNodeAttributes.contains(key) && !attrListNodesAsString.contains(key)) {
                    stringAttribute = new AttributeImpl(key, AttributeType.STRING, key);
                    attrListNodes.add(stringAttribute);
                    attrListNodesAsString.add(key);
                    mapAttributeIdToNodeAttribute.put(key, stringAttribute);

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

        AttributeImpl numberAttribute;
        for (String key : scoresKeys) {
            numberAttribute = new AttributeImpl(key, AttributeType.DOUBLE, key);
            attrListNodes.add(numberAttribute);
            mapAttributeIdToScoreAttribute.put(key, numberAttribute);
        }
        for (String key : weightsKeys) {
            numberAttribute = new AttributeImpl(key, AttributeType.DOUBLE, key);
            attrListNodes.add(numberAttribute);
            mapAttributeIdToWeightAttribute.put(key, numberAttribute);
        }

        graph.getAttributeLists().add(attrListNodes);

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
            Node nodeGexf = graph.createNode(item.getJsonNumber("id").toString());
            if (keySet.contains("label")) {
                nodeGexf.setLabel(item.getString("label"));
            }
            if (keySet.contains("description")) {
                AttributeValue att = new AttributeValueImpl(mapAttributeIdToNodeAttribute.get("description"));
                if (item.isNull("description")) {
                    att.setValue("");
                } else {
                    att.setValue(item.getString("description"));
                }
                nodeGexf.getAttributeValues().add(att);
            }

            if (keySet.contains("url")) {
                AttributeValue att = new AttributeValueImpl(mapAttributeIdToNodeAttribute.get("url"));
                if (item.isNull("url")) {
                    att.setValue("");
                } else {
                    att.setValue(item.getString("url"));
                }
                nodeGexf.getAttributeValues().add(att);
            }

            Position pos = new PositionImpl();

            if (keySet.contains("x")) {
                pos.setX(item.getJsonNumber("x").bigDecimalValue().floatValue());
            }
            if (keySet.contains("y")) {
                pos.setY(item.getJsonNumber("y").bigDecimalValue().floatValue());
            }
            if (keySet.contains("x") && keySet.contains("y")) {
                pos.setZ(0f);
                nodeGexf.setPosition(pos);
            }

            if (keySet.contains("cluster")) {
                AttributeValue att = new AttributeValueImpl(mapAttributeIdToNodeAttribute.get("cluster"));
                if (item.isNull("cluster")) {
                    att.setValue("");
                } else {
                    att.setValue(String.valueOf(item.getInt("cluster")));
                }
                nodeGexf.getAttributeValues().add(att);
            }

            if (keySet.contains("scores")) {
                JsonObject scoresObject = item.getJsonObject("scores");
                for (String key : scoresObject.keySet()) {
                    AttributeValue att = new AttributeValueImpl(mapAttributeIdToScoreAttribute.get(key));
                    if (scoresObject.isNull(key)) {
                        att.setValue("");
                    } else {
                        att.setValue(String.valueOf(scoresObject.getJsonNumber(key).doubleValue()));
                    }
                    nodeGexf.getAttributeValues().add(att);
                }
            }

            if (keySet.contains("weights")) {
                JsonObject weightsObject = item.getJsonObject("weights");
                for (String key : weightsObject.keySet()) {
                    AttributeValue att = new AttributeValueImpl(mapAttributeIdToWeightAttribute.get(key));
                    if (weightsObject.isNull(key)) {
                        att.setValue("");
                    } else {
                        att.setValue(weightsObject.getJsonNumber(key).toString());
                    }
                    nodeGexf.getAttributeValues().add(att);
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
        Set<String> keySet;
        while (iteratorLinks.hasNext()) {
            JsonObject link = (JsonObject) iteratorLinks.next();
            Node node1 = graph.getNode(link.getJsonNumber("source_id").toString());
            Node node2 = graph.getNode(link.getJsonNumber("target_id").toString());
            Edge edge = node1.connectTo(node2);
            edge.setWeight(link.getJsonNumber("strength").bigDecimalValue().floatValue());
        }
    }
}
