package com.github.mamingjie.mybatiscreatetablesql;

import javax.swing.*;

public class CreateSqlGenerateForm {
    private JTextArea codeContent;
    private JPanel rootPanel;

    public void setCodeContentText(String text) {
        codeContent.setText(text);
    }

    public JPanel getRootPanel() {
        return rootPanel;
    }
}
