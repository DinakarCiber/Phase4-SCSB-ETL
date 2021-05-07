package org.recap.camel.datadump;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.io.FilenameUtils;
import org.recap.ScsbCommonConstants;
import org.recap.ScsbConstants;
import org.recap.model.csv.DataDumpFailureReport;

import java.util.List;

/**
 * Created by premkb on 01/10/16.
 */
public class FileNameProcessorForDataDumpFailure implements Processor {

    /**
     * This method is invoked by route to set the data dump file name, report type and institution name in headers for failure data dump.
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        DataDumpFailureReport dataDumpFailureReport = (DataDumpFailureReport) exchange.getIn().getBody();
        List<DataDumpFailureReport> dataDumpFailureReportRecordList = dataDumpFailureReport.getDataDumpFailureReportRecordList();
        String fileName = FilenameUtils.removeExtension(dataDumpFailureReport.getFileName());
        exchange.getIn().setHeader(ScsbCommonConstants.REPORT_FILE_NAME, fileName);
        exchange.getIn().setHeader(ScsbConstants.REPORT_TYPE, dataDumpFailureReport.getReportType());
        exchange.getIn().setHeader(ScsbConstants.DIRECTORY_NAME, dataDumpFailureReport.getInstitutionName());
        exchange.getIn().setBody(dataDumpFailureReportRecordList);
    }
}
