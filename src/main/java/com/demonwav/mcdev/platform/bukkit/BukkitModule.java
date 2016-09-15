package com.demonwav.mcdev.platform.bukkit;

import com.demonwav.mcdev.buildsystem.BuildSystem;
import com.demonwav.mcdev.buildsystem.SourceType;
import com.demonwav.mcdev.insight.generation.GenerationData;
import com.demonwav.mcdev.platform.AbstractModule;
import com.demonwav.mcdev.platform.PlatformType;
import com.demonwav.mcdev.platform.bukkit.generation.BukkitGenerationData;
import com.demonwav.mcdev.platform.bukkit.util.BukkitConstants;
import com.demonwav.mcdev.platform.bukkit.yaml.PluginConfigManager;
import com.demonwav.mcdev.util.McPsiUtil;

import com.google.common.base.Objects;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
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

@SuppressWarnings("unused")
public class BukkitModule<T extends BukkitModuleType> extends AbstractModule {

    private VirtualFile pluginYml;
    private PlatformType type;
    private PluginConfigManager configManager;
    private T moduleType;

    BukkitModule(@NotNull Module module, @NotNull T type) {
        super(module);
        this.moduleType = type;
        this.type = type.getPlatformType();
        buildSystem = BuildSystem.getInstance(module);
        if (buildSystem != null) {
            if (!buildSystem.isImported()) {
                buildSystem.reImport(module).done(buildSystem  -> setup());
            } else {
                if (buildSystem.isFinishImport()) {
                    setup();
                }
            }
        }
    }

    private void setup() {
        pluginYml = buildSystem.findFile("plugin.yml", SourceType.RESOURCE);

        if (pluginYml != null) {
            this.configManager = new PluginConfigManager(this);
        }
    }

    @Nullable
    public PluginConfigManager getConfigManager() {
        if (configManager == null) {
            if (pluginYml != null) {
                configManager = new PluginConfigManager(this);
            }
        }
        return configManager;
    }

    public VirtualFile getPluginYml() {
        if (buildSystem == null) {
            buildSystem = BuildSystem.getInstance(module);
        }
        if (pluginYml == null && buildSystem != null) {
            // try and find the file again if it's not already present
            // when this object was first created it may not have been ready
            pluginYml = buildSystem.findFile("plugin.yml", SourceType.RESOURCE);
        }
        return pluginYml;
    }

    @Override
    public Icon getIcon() {
        return type.getType().getIcon();
    }

    @Override
    public T getModuleType() {
        return moduleType;
    }

    private void setModuleType(@NotNull T moduleType) {
        this.moduleType = moduleType;
    }

    @Override
    public PlatformType getType() {
        return type;
    }

    private void setType(@NotNull PlatformType type) {
        this.type = type;
    }

    @Override
    public boolean isEventClassValid(PsiElement eventClass, String annotation) {
        return (eventClass instanceof PsiClass) &&
                BukkitConstants.BUKKIT_EVENT_CLASS.equals(((PsiClass) eventClass).getQualifiedName());
    }

    @Override
    public String writeErrorMessageForEventParameter(PsiElement eventClass, String annotation) {
        return "Parameter is not a subclass of org.bukkit.event.Event\n" +
                "Compiling and running this listener may result in a runtime exception";
    }

    @Override
    public void doPreEventGenerate(@NotNull PsiClass psiClass, @Nullable GenerationData data) {
        if (!McPsiUtil.extendsOrImplementsClass(psiClass, BukkitConstants.BUKKIT_LISTENER_CLASS)) {
            McPsiUtil.addImplements(psiClass, BukkitConstants.BUKKIT_LISTENER_CLASS, project);
        }
    }

    @Nullable
    @Override
    public PsiMethod generateEventListenerMethod(@NotNull PsiClass containingClass,
                                                 @NotNull PsiClass chosenClass,
                                                 @NotNull String chosenName,
                                                 @Nullable GenerationData data) {
        BukkitGenerationData bukkitData = (BukkitGenerationData) data;
        assert  bukkitData != null;

        PsiMethod method = generateBukkitStyleEventListenerMethod(
            containingClass,
            chosenClass,
            chosenName,
            project,
            BukkitConstants.BUKKIT_HANDLER_ANNOTATION,
            bukkitData.isIgnoreCanceled()
        );

        if (!bukkitData.getEventPriority().equals("NORMAL")) {
            PsiModifierList list = method.getModifierList();
            PsiAnnotation annotation = list.findAnnotation(BukkitConstants.BUKKIT_HANDLER_ANNOTATION);
            if (annotation == null) {
                return method;
            }

            PsiAnnotationMemberValue value = JavaPsiFacade.getElementFactory(project)
                .createExpressionFromText(BukkitConstants.BUKKIT_EVENT_PRIORITY_CLASS + "." + bukkitData.getEventPriority(), annotation);

            annotation.setDeclaredAttributeValue("priority", value);
        }

        return method;
    }

    public static PsiMethod generateBukkitStyleEventListenerMethod(@NotNull PsiClass containingClass,
                                                                   @NotNull PsiClass chosenClass,
                                                                   @NotNull String chosenName,
                                                                   @NotNull Project project,
                                                                   @NotNull String annotationName,
                                                                   boolean setIgnoreCancelled) {
        PsiMethod newMethod = JavaPsiFacade.getElementFactory(project).createMethod(chosenName, PsiType.VOID);

        PsiParameterList list = newMethod.getParameterList();
        PsiParameter parameter = JavaPsiFacade.getElementFactory(project)
            .createParameter(
                "event",
                PsiClassType.getTypeByName(chosenClass.getQualifiedName(), project, GlobalSearchScope.allScope(project))
            );
        list.add(parameter);

        PsiModifierList modifierList = newMethod.getModifierList();
        PsiAnnotation annotation = modifierList.addAnnotation(annotationName);

        if (setIgnoreCancelled) {
            PsiAnnotationMemberValue value = JavaPsiFacade.getElementFactory(project).createExpressionFromText("true", annotation);
            annotation.setDeclaredAttributeValue("ignoreCancelled", value);
        }

        return newMethod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BukkitModule<?> that = (BukkitModule<?>) o;
        return Objects.equal(pluginYml, that.pluginYml) &&
            type == that.type &&
            Objects.equal(configManager, that.configManager) &&
            Objects.equal(moduleType, that.moduleType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(pluginYml, type, configManager, moduleType);
    }
}
