package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

interface ChapterActionsState {

    void rebuild();

    List<ResourceLocation> questIdsInChapter(String chapter);

    List<ResourceLocation> questIdsInCategory(String categoryId);

    int chapterQuestCount(String chapter);

    void setSelectedChapter(String chapter);

    void invalidateChapterCaches();

    void openContextMenuFor(int mx, int my, List<SidebarPanel.MenuAction> actions);

    Screen thisScreen();
}
