package com.nodecraft.gui.components;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class PropertySectionOrganizer {

    private PropertySectionOrganizer() {
    }

    static OrganizedProperties organize(List<PropertyDescriptor> properties) {
        Map<String, List<PropertyDescriptor>> groupedProperties = properties.stream()
                .collect(Collectors.groupingBy(prop -> PropertyCategoryFormatter.normalize(prop.category)));

        List<PropertyDescriptor> generalProperties = List.of();
        if (groupedProperties.containsKey("")) {
            generalProperties = List.copyOf(groupedProperties.remove(""));
        }

        List<String> categories = new ArrayList<>(groupedProperties.keySet());
        categories.sort(Comparator.naturalOrder());

        List<PropertySection> sections = categories.stream()
                .map(category -> new PropertySection(
                        category,
                        PropertyCategoryFormatter.format(category),
                        List.copyOf(groupedProperties.get(category))
                ))
                .toList();

        return new OrganizedProperties(generalProperties, sections);
    }

    record OrganizedProperties(
            List<PropertyDescriptor> generalProperties,
            List<PropertySection> sections
    ) {
    }

    record PropertySection(
            String categoryKey,
            String displayName,
            List<PropertyDescriptor> properties
    ) {
    }
}
