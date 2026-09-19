package com.nodecraft.gui.components.property.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class PropertySectionOrganizer {

    private PropertySectionOrganizer() {
    }

    public static OrganizedProperties organize(List<PropertyDescriptor> properties) {
        Map<String, List<PropertyDescriptor>> groupedProperties = properties.stream()
                .collect(Collectors.groupingBy(prop -> PropertyCategoryFormatter.normalize(prop.category)));

        List<PropertyDescriptor> generalProperties = List.of();
        if (groupedProperties.containsKey("")) {
            generalProperties = List.copyOf(groupedProperties.remove(""));
        }

        List<String> categories = new ArrayList<>(groupedProperties.keySet());
        categories.sort(sectionComparator());

        List<PropertySection> sections = categories.stream()
                .map(category -> new PropertySection(
                        category,
                        PropertyCategoryFormatter.format(category),
                        List.copyOf(groupedProperties.get(category)),
                        AdvancedPropertyCategories.collapsedByDefault(category)
                ))
                .toList();

        return new OrganizedProperties(generalProperties, sections);
    }

    /** Advanced / compatibility sections sort last and start collapsed. */
    public static boolean isCollapsedByDefault(String categoryKey) {
        return AdvancedPropertyCategories.collapsedByDefault(categoryKey);
    }

    private static Comparator<String> sectionComparator() {
        return (a, b) -> {
            boolean aDeferred = AdvancedPropertyCategories.isAdvanced(a);
            boolean bDeferred = AdvancedPropertyCategories.isAdvanced(b);
            if (aDeferred != bDeferred) {
                return aDeferred ? 1 : -1;
            }
            return a.compareToIgnoreCase(b);
        };
    }

    public record OrganizedProperties(
            List<PropertyDescriptor> generalProperties,
            List<PropertySection> sections
    ) {
    }

    public record PropertySection(
            String categoryKey,
            String displayName,
            List<PropertyDescriptor> properties,
            boolean collapsedByDefault
    ) {
        public PropertySection(String categoryKey, String displayName, List<PropertyDescriptor> properties) {
            this(categoryKey, displayName, properties, AdvancedPropertyCategories.collapsedByDefault(categoryKey));
        }
    }
}
