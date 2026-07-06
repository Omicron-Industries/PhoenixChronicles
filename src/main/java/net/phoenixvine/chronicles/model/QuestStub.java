package net.phoenixvine.chronicles.model;

import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;

/**
 * @param filePath Keeps track of where the file is for later
 */
public record QuestStub(ResourceLocation id, String category, ResourceLocation parentId, String shape, int x, int y,
                        Path filePath) {}
