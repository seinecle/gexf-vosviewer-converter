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
public class TemplateItem {

    private String descriptionHeading;
    private String descriptionLabel;
    private String descriptionText;

    public String getDescriptionHeading() {
        return descriptionHeading;
    }

    public void setDescriptionHeading(String descriptionHeading) {
        this.descriptionHeading = descriptionHeading;
    }

    public String getDescriptionLabel() {
        return descriptionLabel;
    }

    public void setDescriptionLabel(String descriptionLabel) {
        this.descriptionLabel = descriptionLabel;
    }

    public String getDescriptionText() {
        return descriptionText;
    }

    public void setDescriptionText(String descriptionText) {
        this.descriptionText = descriptionText;
    }

    public String fullDescriptionItem() {
        String toReturn = "<div class='description_heading'>" + descriptionHeading + "</div><div class='description_label'>{label}</div>";
        if (!descriptionText.isBlank()) {
            toReturn = toReturn + "<div class='description_text'>" + descriptionText + "</div>";
        }
        return toReturn;
    }
}