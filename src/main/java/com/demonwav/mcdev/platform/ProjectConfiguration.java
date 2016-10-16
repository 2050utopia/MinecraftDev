/*
 * Minecraft Dev for IntelliJ
 *
 * https://minecraftdev.org
 *
 * Copyright (c) 2016 Kyle Wood (DemonWav)
 *
 * MIT License
 */

package com.demonwav.mcdev.platform;

import com.demonwav.mcdev.buildsystem.BuildSystem;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public abstract class ProjectConfiguration {

    public String pluginName = null;
    public String pluginVersion = null;
    public String mainClass = null;
    public String description = null;
    public final List<String> authors = new ArrayList<>();
    public String website = null;
    public PlatformType type = null;

    public Module module;

    public boolean isFirst = false;

    public abstract void create(@NotNull Project project, @NotNull BuildSystem buildSystem, @NotNull ProgressIndicator indicator);

    public boolean hasAuthors() {
        return listContainsAtLeastOne(this.authors);
    }

    public void setAuthors(String string) {
        this.authors.clear();
        Collections.addAll(this.authors, commaSplit(string));
    }

    public boolean hasDescription() {
        return description != null && !description.trim().isEmpty();
    }

    @NotNull
    protected static String[] commaSplit(@NotNull String string) {
        return string.trim().replaceAll("\\[|\\]", "").split("\\s*,\\s*");
    }

    @Contract("null -> false")
    protected static boolean listContainsAtLeastOne(List<String> list) {
        if (list == null || list.size() == 0) {
            return false;
        }

        final Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            String s = it.next();
            if (s.trim().isEmpty()) {
                it.remove();
            }
        }

        return list.size() != 0;
    }

    protected VirtualFile getMainClassDirectory(String[] files, VirtualFile file) {
        try {
            for (int i = 0, len = files.length - 1; i < len; i++) {
                String s = files[i];
                VirtualFile temp = file.findChild(s);
                if (temp != null && temp.isDirectory()) {
                    file = temp;
                } else {
                    file = file.createChildDirectory(this, s);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }
}
