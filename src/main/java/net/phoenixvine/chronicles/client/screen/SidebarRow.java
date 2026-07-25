package net.phoenixvine.chronicles.client.screen;

record SidebarRow(boolean isFolder, String id, String label, int y, int height, boolean inFolder,
                  boolean collapsed, boolean subChapter, boolean locked) {}
