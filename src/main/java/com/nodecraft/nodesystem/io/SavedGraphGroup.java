package com.nodecraft.nodesystem.io;

import java.util.List;

/**
 * Editor-only group metadata stored on {@link SavedGraph}.
 */
public class SavedGraphGroup {
    public String id;
    public String title;
    public float x;
    public float y;
    public float width;
    public float height;
    public String color;
    public boolean collapsed;
    public boolean locked;
    public List<String> nodeIds;

    public SavedGraphGroup() {
    }
}
