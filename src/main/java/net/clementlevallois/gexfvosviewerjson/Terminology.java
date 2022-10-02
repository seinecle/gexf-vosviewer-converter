/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.gexfvosviewerjson;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;



/**
 *
 * @author LEVALLOIS
 */
public class Terminology {
    
    private String item = "";
    private String items = "";
    private String link = "";
    private String links = "";
    private String link_strength = "";
    private String total_link_strength = "";

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public String getItems() {
        return items;
    }

    public void setItems(String items) {
        this.items = items;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getLinks() {
        return links;
    }

    public void setLinks(String links) {
        this.links = links;
    }

    public String getLink_strength() {
        return link_strength;
    }

    public void setLink_strength(String link_strength) {
        this.link_strength = link_strength;
    }

    public String getTotal_link_strength() {
        return total_link_strength;
    }

    public void setTotal_link_strength(String total_link_strength) {
        this.total_link_strength = total_link_strength;
    }
    

    
    public JsonObjectBuilder getTerminology(){
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("item", item);
        job.add("items", items);
        job.add("link", link);
        job.add("links", links);
        job.add("link_strength", link_strength);
        job.add("total_link_strength", total_link_strength);
        
        return job;
    }
    
}
