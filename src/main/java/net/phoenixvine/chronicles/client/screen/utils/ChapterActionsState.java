package net.phoenixvine.chronicles.client.screen.utils;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.screen.widgets.SidebarPanel;


import java.util.List;

public interface ChapterActionsState {

    void rebuild();

    List<ResourceLocation> questIdsInChapter(String chapter);

    List<ResourceLocation> questIdsInCategory(String categoryId);

    int chapterQuestCount(String chapter);

    void setSelectedChapter(String chapter);

    void invalidateChapterCaches();

    void openContextMenuFor(int mx, int my, List<SidebarPanel.MenuAction> actions);

    Screen thisScreen();
}
