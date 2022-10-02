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
public class Metadata {
    
    private String authorCanBePlural = "";
    private String dateOfCreation = "";
    private String descriptionOfData = "";
    private String titleViz = "";
    private String furtherInfo = "";

    public String getAuthorCanBePlural() {
        return authorCanBePlural;
    }

    public void setAuthorCanBePlural(String authorCanBePlural) {
        this.authorCanBePlural = authorCanBePlural;
    }

    public String getDateOfCreation() {
        return dateOfCreation;
    }

    public void setDateOfCreation(String dateOfCreation) {
        this.dateOfCreation = dateOfCreation;
    }

    public String getDescriptionOfData() {
        return descriptionOfData;
    }

    public void setDescriptionOfData(String descriptionOfData) {
        this.descriptionOfData = descriptionOfData;
    }

    public String getTitleViz() {
        return titleViz;
    }

    public void setTitleViz(String titleViz) {
        this.titleViz = titleViz;
    }

    public String getFurtherInfo() {
        return furtherInfo;
    }

    public void setFurtherInfo(String furtherInfo) {
        this.furtherInfo = furtherInfo;
    }
    
    public JsonObjectBuilder getMetadata(){
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("author(s)", authorCanBePlural);
        job.add("date of creation", dateOfCreation);
        job.add("description of the data", descriptionOfData);
        job.add("title of the visualization", titleViz);
        job.add("further info", furtherInfo);
     
        return job;
    }
    
}
