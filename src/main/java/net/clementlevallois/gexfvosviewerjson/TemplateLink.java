/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.gexfvosviewerjson;

/**
 *
 * @author LEVALLOIS
 */
public class TemplateLink {

    private String descriptionHeading;
    private String descriptionLabelSource;
    private String descriptionTextSource;
    private String descriptionLabelTarget;
    private String descriptionTextTarget;

    public String getDescriptionHeading() {
        return descriptionHeading;
    }

    public void setDescriptionHeading(String descriptionHeading) {
        this.descriptionHeading = descriptionHeading;
    }

    public String getDescriptionLabelSource() {
        return descriptionLabelSource;
    }

    public void setDescriptionLabelSource(String descriptionLabelSource) {
        this.descriptionLabelSource = descriptionLabelSource;
    }

    public String getDescriptionTextSource() {
        return descriptionTextSource;
    }

    public void setDescriptionTextSource(String descriptionTextSource) {
        this.descriptionTextSource = descriptionTextSource;
    }

    public String getDescriptionLabelTarget() {
        return descriptionLabelTarget;
    }

    public void setDescriptionLabelTarget(String descriptionLabelTarget) {
        this.descriptionLabelTarget = descriptionLabelTarget;
    }

    public String getDescriptionTextTarget() {
        return descriptionTextTarget;
    }

    public void setDescriptionTextTarget(String descriptionTextTarget) {
        this.descriptionTextTarget = descriptionTextTarget;
    }

    public String fullDescriptionLink() {
        String toReturn = "<div class='description_heading'>" + descriptionHeading + "</div>";
        return toReturn;
    }

}
