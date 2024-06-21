package com.github.mamingjie.mybatiscreatetablesql;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PsiUtilBase;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CreateSqlAction extends AnAction {
    public static Pattern compile = Pattern.compile("[A-Z]");

    public static String underlineByHump(String str) {

        Matcher matcher = compile.matcher(str);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, (matcher.start() == 0 ? "" : "_") + matcher.group(0).toLowerCase());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static String getStringValue(PsiAnnotationMemberValue annotationValue) {
        if (annotationValue == null) {
            return null;
        }
        String value = annotationValue.getText().replaceAll("[\"`]", "");
        return value.isEmpty() ? null : value;
    }

    public static String getFieldType(PsiType psiType, String fieldName) {
        if (psiType instanceof PsiClassReferenceType referenceType) {
            String className = referenceType.getClassName();
            switch (className) {
                case "Integer":
                    return endsWithAny(fieldName.toLowerCase(), "status", "type") ? "tinyint(1)" : "int";
                case "Boolean":
                    return "tinyint(1)";
                case "Long":
                    return "bigint";
                case "BigDecimal":
                    return "decimal(10, 2)";
                case "String":
                    return "varchar(255)";
                case "Date":
                    return "datetime";

            }
        }
        return "";
    }

    public static boolean endsWithAny(String str, String... keys) {
        for (String key : keys) {
            if (str.endsWith(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        // Messages.showMessageDialog(project,"Hello,World","First Action", Messages.getInformationIcon());
        CreateSqlGenerateForm testDialog = new CreateSqlGenerateForm();
        DialogBuilder dialogBuilder = new DialogBuilder(project);
        dialogBuilder.setCenterPanel(testDialog.getRootPanel());
        dialogBuilder.setTitle("建表语句");

        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        if (editor == null || project == null) {
            dialogBuilder.show();
            return;
        }
        PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
        if (psiFile == null) {
            dialogBuilder.show();
            return;
        }
        StringBuilder createSql = new StringBuilder();
        StringBuilder modifySql = new StringBuilder();
        for (PsiElement psiElement : psiFile.getChildren()) {
            PsiAnnotation annotation;
            PsiAnnotationMemberValue annotationValue;
            String annotationStringValue;
            if (psiElement instanceof PsiClass psiClass) {
                annotation = psiClass.getAnnotation("com.baomidou.mybatisplus.annotation.TableName");
                String tableName = null;
                if (annotation != null) {
                    annotationValue = annotation.findAttributeValue("value");
                    annotationStringValue = getStringValue(annotationValue);
                    if (annotationStringValue != null) {
                        tableName = annotationStringValue;
                    }
                }
                if (tableName == null) {
                    tableName = underlineByHump(psiClass.getName());
                }
                createSql.append("drop table if exists `").append(tableName).append("`;\n\n\n");
                createSql.append("create table `").append(tableName).append("` (\n");
                String tableComment = null;
                annotation = psiClass.getAnnotation("io.swagger.annotations.ApiModel");
                annotationStringValue = getAnnotationValue(annotation, "description", "value");
                if (annotationStringValue != null) {
                    tableComment = annotationStringValue;
                }
                annotation = psiClass.getAnnotation("io.swagger.v3.oas.annotations.media.Schema");
                annotationStringValue = getAnnotationValue(annotation, "description", "name", "title");
                if (annotationStringValue != null) {
                    tableComment = annotationStringValue;
                }
                if (tableComment == null) {
                    tableComment = psiClass.getName();
                }
                PsiField[] fields = psiClass.getAllFields();
                String primaryKey = null;
                for (PsiField field : fields) {
                    if (field.getModifierList() != null && field.getModifierList().hasExplicitModifier(PsiModifier.STATIC)) {
                        continue;
                    }
                    StringBuilder fieldSql = new StringBuilder();
                    String fieldName = underlineByHump(field.getName());
                    String fieldType;
                    String fieldComment = null;
                    annotation = field.getAnnotation("io.swagger.annotations.ApiModelProperty");
                    annotationStringValue = getAnnotationValue(annotation, "value");
                    if (annotationStringValue != null) {
                        fieldComment = annotationStringValue;
                    }
                    annotation = field.getAnnotation("io.swagger.v3.oas.annotations.media.Schema");
                    annotationStringValue = getAnnotationValue(annotation, "description", "name", "title");
                    if (annotationStringValue != null) {
                        fieldComment = annotationStringValue;
                    }
                    annotation = field.getAnnotation("com.baomidou.mybatisplus.annotation.TableId");
                    if (annotation != null) {
                        annotationValue = annotation.findAttributeValue("value");
                        annotationStringValue = getStringValue(annotationValue);
                        if (annotationStringValue != null) {
                            fieldName = annotationStringValue;
                        }
                        fieldType = getFieldType(field.getType(), fieldName);
                        primaryKey = fieldName;
                        annotationValue = annotation.findAttributeValue("type");
                        annotationStringValue = getStringValue(annotationValue);
                        boolean autoIncrement = annotationStringValue != null && annotationStringValue.contains("AUTO");
                        fieldSql.append("    `").append(fieldName).append("` ").append(fieldType).append(" not null");
                        if (autoIncrement) {
                            fieldSql.append(" auto_increment");
                        }
                        fieldSql.append(" comment '").append(fieldComment == null ? field.getName() : fieldComment).append("',\n");
                    } else {
                        annotation = field.getAnnotation("com.baomidou.mybatisplus.annotation.TableField");
                        if (annotation != null) {
                            annotationValue = annotation.findAttributeValue("exist");
                            annotationStringValue = getStringValue(annotationValue);
                            if ("false".equals(annotationStringValue)) {
                                continue;
                            }
                            annotationValue = annotation.findAttributeValue("value");
                            annotationStringValue = getStringValue(annotationValue);
                            if (annotationStringValue != null) {
                                fieldName = annotationStringValue;
                            }
                        }
                        fieldType = getFieldType(field.getType(), fieldName);
                        fieldSql.append("    `").append(fieldName).append("` ").append(fieldType);
                        if ("tinyint(1)".equals(fieldType)) {
                            fieldSql.append(" default 0");
                        }
                        if ("createTime".equals(field.getName())) {
                            fieldSql.append(" default current_timestamp");
                        }
                        if ("updateTime".equals(field.getName())) {
                            fieldSql.append(" default current_timestamp on update current_timestamp");
                        }
                        fieldSql.append(" comment '").append(fieldComment == null ? field.getName() : fieldComment).append("'");
                    }
                    createSql.append(fieldSql).append(",\n");
                    modifySql.append("alter table `").append(tableName).append("` add column").append(fieldSql).append(";\n\n");
                }
                if (primaryKey != null) {
                    createSql.append("    primary key (`").append(primaryKey).append("`)\n");
                } else {
                    createSql.deleteCharAt(createSql.length() - 2);
                }
                createSql.append(") comment '").append(tableComment).append("';\n\n\n").append(modifySql);
            }
        }
        testDialog.setCodeContentText(createSql.toString());
        dialogBuilder.show();
    }

    private String getAnnotationValue(PsiAnnotation annotation, String... keys) {
        if (annotation == null) {
            return null;
        }
        for (String key : keys) {
            PsiAnnotationMemberValue annotationValue = annotation.findAttributeValue(key);
            String annotationStringValue = getStringValue(annotationValue);
            if (annotationStringValue != null) {
                return annotationStringValue;
            }
        }
        return null;
    }

}
