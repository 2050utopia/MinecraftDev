package com.demonwav.mcdev.platform.forge;

import com.demonwav.mcdev.asset.PlatformAssets;
import com.demonwav.mcdev.buildsystem.BuildSystem;
import com.demonwav.mcdev.buildsystem.SourceType;
import com.demonwav.mcdev.insight.generation.GenerationData;
import com.demonwav.mcdev.platform.AbstractModule;
import com.demonwav.mcdev.platform.PlatformType;
import com.demonwav.mcdev.util.McPsiUtil;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public class ForgeModule extends AbstractModule {

    private VirtualFile mcmod;

    ForgeModule(@NotNull Module module) {
        super(module);
        this.buildSystem = BuildSystem.getInstance(module);
        if (buildSystem != null) {
            if (!buildSystem.isImported()) {
                buildSystem.reImport(module).done(buildSystem -> mcmod = buildSystem.findFile("mcmod.info", SourceType.RESOURCE));
            }
        }
    }

    @Override
    public ForgeModuleType getModuleType() {
        return ForgeModuleType.getInstance();
    }

    @Override
    public PlatformType getType() {
        return PlatformType.FORGE;
    }

    @Override
    public Icon getIcon() {
        return PlatformAssets.FORGE_ICON;
    }

    @Override
    public boolean isEventClassValid(PsiElement eventClass, String annotation) {
        if (!(eventClass instanceof PsiClass)) {
            return false;
        }

        final PsiClass psiClass = (PsiClass) eventClass;

        if (annotation == null ) {
            return "net.minecraftforge.fml.common.event.FMLEvent".equals(psiClass.getQualifiedName()) ||
                "net.minecraftforge.fml.common.eventhandler.Event".equals(psiClass.getQualifiedName());
        }

        if (annotation.equals("net.minecraftforge.fml.common.Mod.EventHandler")) {
            return "net.minecraftforge.fml.common.event.FMLEvent".equals(psiClass.getQualifiedName());
        }

        if (annotation.equals("net.minecraftforge.fml.common.eventhandler.SubscribeEvent")) {
            return "net.minecraftforge.fml.common.eventhandler.Event".equals(psiClass.getQualifiedName());
        }

        // just default to true
        return true;
    }

    @Override
    public String writeErrorMessageForEventParameter(PsiElement eventClass, String annotation) {
        if (annotation.equals("net.minecraftforge.fml.common.Mod.EventHandler")) {
            return "Parameter is not a subclass of net.minecraftforge.fml.common.event.FMLEvent\n" +
                    "Compiling and running this listener may result in a runtime exception";
        }

        return "Parameter is not a subclass of net.minecraftforge.fml.common.eventhandler.Event\n" +
                "Compiling and running this listener may result in a runtime exception";
    }

    public VirtualFile getMcmod() {
        if (buildSystem == null) {
            buildSystem = BuildSystem.getInstance(module);
        }
        if (mcmod == null && buildSystem != null) {
            // try and find the file again if it's not already present
            // when this object was first created it may not have been ready
            mcmod = buildSystem.findFile("mcmod.info", SourceType.RESOURCE);
        }
        return mcmod;
    }

    @Nullable
    @Override
    public PsiMethod generateEventListenerMethod(@NotNull PsiClass containingClass,
                                                 @NotNull PsiClass chosenClass,
                                                 @NotNull String chosenName,
                                                 @Nullable GenerationData data) {
        boolean isFmlEvent = McPsiUtil.extendsOrImplementsClass(chosenClass, "net.minecraftforge.fml.common.event.FMLEvent");

        PsiMethod method = JavaPsiFacade.getElementFactory(project).createMethod(chosenName, PsiType.VOID);
        PsiParameterList parameterList = method.getParameterList();

        PsiParameter parameter = JavaPsiFacade.getElementFactory(project)
            .createParameter(
                "event",
                PsiClassType.getTypeByName(chosenClass.getQualifiedName(), project, GlobalSearchScope.allScope(project))
            );

        parameterList.add(parameter);
        PsiModifierList modifierList = method.getModifierList();

        if (isFmlEvent) {
            modifierList.addAnnotation("net.minecraftforge.fml.common.Mod.EventHandler");
        } else {
            modifierList.addAnnotation("net.minecraftforge.fml.common.eventhandler.SubscribeEvent");
        }

        return method;
    }
}
