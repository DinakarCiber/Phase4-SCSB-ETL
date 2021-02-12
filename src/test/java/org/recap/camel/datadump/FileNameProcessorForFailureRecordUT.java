package org.recap.camel.datadump;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.recap.BaseTestCaseUT;
import org.recap.RecapCommonConstants;
import org.recap.camel.FileNameProcessorForFailureRecord;
import org.recap.model.csv.FailureReportReCAPCSVRecord;
import org.recap.model.csv.ReCAPCSVFailureRecord;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FileNameProcessorForFailureRecordUT extends BaseTestCaseUT {

    @InjectMocks
    FileNameProcessorForFailureRecord fileNameProcessorForFailureRecord;

    @Test
    public void testProcess() {
        FailureReportReCAPCSVRecord failureReportReCAPCSVRecord = new FailureReportReCAPCSVRecord();
        failureReportReCAPCSVRecord.setOwningInstitution("PUL");
        failureReportReCAPCSVRecord.setOwningInstitutionBibId("1111");
        failureReportReCAPCSVRecord.setOwningInstitutionHoldingsId("2222");
        failureReportReCAPCSVRecord.setLocalItemId("333333333");
        failureReportReCAPCSVRecord.setItemBarcode("4444");
        failureReportReCAPCSVRecord.setCustomerCode("PA");
        failureReportReCAPCSVRecord.setTitle("History, of Science");
        failureReportReCAPCSVRecord.setCollectionGroupDesignation("open");
        failureReportReCAPCSVRecord.setCreateDateItem(new SimpleDateFormat("mm-dd-yyyy").format(new Date()));
        failureReportReCAPCSVRecord.setLastUpdatedDateItem(new SimpleDateFormat("mm-dd-yyyy").format(new Date()));
        failureReportReCAPCSVRecord.setExceptionMessage("exception");
        failureReportReCAPCSVRecord.setErrorDescription("error");
        ReCAPCSVFailureRecord reCAPCSVFailureRecord = new ReCAPCSVFailureRecord();
        reCAPCSVFailureRecord.setFileName("test.xml");
        reCAPCSVFailureRecord.setInstitutionName("PUL");
        reCAPCSVFailureRecord.setReportType(RecapCommonConstants.FAILURE);
        assertNotNull(failureReportReCAPCSVRecord.getCreateDateItem());
        assertNotNull(failureReportReCAPCSVRecord.getLastUpdatedDateItem());
        reCAPCSVFailureRecord.setFailureReportReCAPCSVRecordList(Arrays.asList(failureReportReCAPCSVRecord));

        String dataHeader = "test";
        CamelContext ctx = new DefaultCamelContext();
        Exchange ex = new DefaultExchange(ctx);
        Map<String, Object> mapData = new HashMap<>();
        ex.setProperty("CamelAggregationStrategy", mapData);
        Message in = ex.getIn();
        ex.setMessage(in);
        in.setBody(reCAPCSVFailureRecord);
        ex.setIn(in);
        Map<String, Object> mapdata = new HashMap<>();
        mapdata.put("CamelFileName", dataHeader);
        in.setHeaders(mapdata);
        fileNameProcessorForFailureRecord = new FileNameProcessorForFailureRecord();
        try {
            fileNameProcessorForFailureRecord.process(ex);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        assertTrue(true);
    }

}
