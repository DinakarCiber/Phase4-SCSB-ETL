package org.recap.controller.version;

import org.junit.Test;
import org.mockito.InjectMocks;
import org.recap.BaseTestCaseUT;

import static org.junit.Assert.assertNotNull;

/**
 * Created by hemalathas on 19/4/17.
 */
public class VersionNumberUT extends BaseTestCaseUT {

    @InjectMocks
    VersionNumberController versionNumber;

    @Test
    public void testVersionNumber() {
        versionNumber.setVersionNumber("1.0.0");
        String version = versionNumber.getVersionNumber();
        assertNotNull(version);
    }

}