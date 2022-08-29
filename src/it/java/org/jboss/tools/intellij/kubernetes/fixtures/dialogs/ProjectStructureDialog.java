/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.intellij.kubernetes.fixtures.dialogs;

import com.intellij.remoterobot.RemoteRobot;
import com.intellij.remoterobot.data.RemoteComponent;
import com.intellij.remoterobot.fixtures.CommonContainerFixture;
import com.intellij.remoterobot.fixtures.DefaultXpath;
import com.intellij.remoterobot.fixtures.FixtureName;
import com.intellij.remoterobot.utils.WaitForConditionTimeoutException;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Project Structure dialog fixture
 *
 * @author olkornii@redhat.com
 */
@DefaultXpath(by = "MyDialog type", xpath = "//div[@accessiblename='Project Structure' and @class='MyDialog']")
@FixtureName(name = "Project Structure Dialog")
public class ProjectStructureDialog extends CommonContainerFixture {
    public ProjectStructureDialog(@NotNull RemoteRobot remoteRobot, @NotNull RemoteComponent remoteComponent) {
        super(remoteRobot, remoteComponent);
    }

    /**
     * Cancel the 'Project Structure' dialog if it appears
     *
     * @param remoteRobot reference to the RemoteRobot instance
     */
    public static void cancelProjectStructureDialogIfItAppears(RemoteRobot remoteRobot) {
        try {
            ProjectStructureDialog projectStructureDialog = remoteRobot.find(ProjectStructureDialog.class, Duration.ofSeconds(20));
            projectStructureDialog.button("Cancel").click();
        } catch (WaitForConditionTimeoutException e) {
            e.printStackTrace();
        }
    }
}
