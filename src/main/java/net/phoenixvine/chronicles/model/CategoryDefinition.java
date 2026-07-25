package net.phoenixvine.chronicles.model;

import java.util.List;

public record CategoryDefinition(String id, String displayName, List<String> chapters) {}
