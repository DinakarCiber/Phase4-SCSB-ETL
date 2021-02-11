package org.recap.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.recap.BaseTestCaseUT;
import org.recap.RecapCommonConstants;
import org.recap.model.jparw.ReportEntity;
import org.recap.repositoryrw.ReportDetailRepository;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class ReportProcessorUT extends BaseTestCaseUT {
    @InjectMocks
    ReportProcessor reportProcessor;

    ReportEntity reportEntity;

    @Mock
    ReportDetailRepository reportDetailRepository;

    @Before
    public void setUpBefore() {
        reportEntity = new ReportEntity();
        reportEntity.setFileName("test");
        reportEntity.setInstitutionName("NYPL");
        reportEntity.setType(RecapCommonConstants.FAILURE);
        reportEntity.setCreatedDate(new Date());
    }

    @Test
    public void testProcess() {
        String dataHeader = ";currentPageCount#1";
        CamelContext ctx = new DefaultCamelContext();
        Exchange ex = new DefaultExchange(ctx);
        Message in = ex.getIn();
        in.setBody(reportEntity);
        ex.setMessage(in);
        ex.setIn(in);
        Map<String, Object> mapdata = new HashMap<>();
        mapdata.put("batchHeaders", dataHeader);
        try {
            reportProcessor.process(ex);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        assertTrue(true);
    }
}
