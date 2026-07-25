package net.phoenixvine.chronicles.model;

import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;

public record QuestStub(ResourceLocation id, String category, ResourceLocation parentId, String shape, int x, int y,
                        Path filePath) {}
