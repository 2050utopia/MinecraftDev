package com.demonwav.mcdev.insight;

import com.demonwav.mcdev.MinecraftSettings;
import com.demonwav.mcdev.platform.AbstractModuleType;
import com.demonwav.mcdev.platform.MinecraftModule;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class ListenerEventAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!MinecraftSettings.getInstance().isShowEventListenerGutterIcons()) {
            return;
        }

        // Since we want to line up with the method declaration, not the annotation
        // declaration, we need to target identifiers, not just PsiMethods.
        if (!(element instanceof PsiIdentifier && (element.getParent() instanceof PsiMethod))) {
            return;
        }
        // The PsiIdentifier is going to be a method of course!
        PsiMethod method = (PsiMethod) element.getParent();
        if (method.hasModifierProperty(PsiModifier.ABSTRACT) || method.hasModifierProperty(PsiModifier.STATIC)) {
            // I don't think any implementation allows for abstract or static method listeners.
            return;
        }
        PsiModifierList modifierList = method.getModifierList();
        Module module = ModuleUtilCore.findModuleForPsiElement(element);
        if (module == null) {
            return;
        }
        MinecraftModule instance = MinecraftModule.getInstance(module);
        if (instance == null) {
            return;
        }
        // Since each platform has their own valid listener annotations,
        // some platforms may have multiple allowed annotations for various cases
        final Collection<AbstractModuleType<?>> moduleTypes = instance.getTypes();
        boolean contains = false;
        String annotation = null;
        for (AbstractModuleType<?> moduleType : moduleTypes) {
            final List<String> listenerAnnotations = moduleType.getListenerAnnotations();
            for (String listenerAnnotation : listenerAnnotations) {
                if (modifierList.findAnnotation(listenerAnnotation) != null) {
                    contains = true;
                    annotation = listenerAnnotation;
                    break;
                }
            }

            if (contains) {
                break;
            }
        }
        if (!contains) {
            return;
        }

        final PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length < 1) {
            return;
        }
        final PsiParameter eventParameter = parameters[0];
        if (eventParameter == null) {
            // Listeners must have at least a single parameter
            return;
        }
        // Get the type of the parameter so we can start resolving it
        PsiTypeElement psiEventElement = eventParameter.getTypeElement();
        if (psiEventElement == null) {
            return;
        }
        final PsiType type = psiEventElement.getType();
        // Validate that it is a class reference type, I don't know if this will work with
        // other JVM languages such as Kotlin or Scala, but it might!
        if (!(type instanceof PsiClassReferenceType)) {
            return;
        }
        // And again, make sure that we can at least resolve the type, otherwise it's not a valid
        // class reference.
        final PsiClass eventClass = ((PsiClassReferenceType) type).resolve();
        if (eventClass == null) {
            return;
        }

        if (instance.isEventClassValid(eventClass, annotation)) {
            return;
        }

        if (!isSuperEventListenerAllowed(eventClass, annotation, instance)) {
            holder.createErrorAnnotation(eventParameter, instance.writeErrorMessageForEvent(eventClass, annotation));
        }
    }

    public static boolean isSuperEventListenerAllowed(PsiElement eventClass, String annotation, MinecraftModule module) {
        if (!(eventClass instanceof PsiClass)) {
            return false;
        }

        final PsiClass psiClass = (PsiClass) eventClass;
        final PsiClass[] supers = psiClass.getSupers();
        for (PsiClass aSuper : supers) {
            if (module.isEventClassValid(aSuper, annotation)) {
                return true;
            }
            if (isSuperEventListenerAllowed(aSuper, annotation, module)) {
                return true;
            }
        }
        return false;
    }
}
