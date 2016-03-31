package org.jenkinsci.test.acceptance.po;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.jenkinsci.test.acceptance.Matchers.hasContent;

/**
 * Page object for the tool configuration page.
 */
public class JenkinsToolConfig extends JenkinsConfig {
    public JenkinsToolConfig(Jenkins jenkins) {
        super(jenkins, "configureTools");
    }

    public void configure() {
        jenkins.toolConfigure();
    }

    public void save() {
        clickButton("Save");
        assertThat(driver, not(hasContent("This page expects a form submission")));
    }

    public <T extends ToolInstallation> T addTool(Class<T> type) {
        jenkins.ensureToolConfigPage();

        String name = type.getAnnotation(ToolInstallationPageObject.class).name();

        clickButton("Add " + name);
        elasticSleep(100);
        String path = find(by.button("Delete " + name)).getAttribute("path");
        String prefix = path.substring(0, path.length() - 18);

        return newInstance(type, this, prefix);
    }
}
