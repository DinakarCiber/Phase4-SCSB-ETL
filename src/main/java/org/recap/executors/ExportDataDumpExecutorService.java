package org.recap.executors;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.recap.ReCAPConstants;
import org.recap.camel.EmailPayLoad;
import org.recap.model.etl.ExportDataDumpCallable;
import org.recap.model.export.DataDumpRequest;
import org.recap.model.jaxb.JAXBHandler;
import org.recap.model.jaxb.marc.BibRecords;
import org.recap.repository.BibliographicDetailsRepository;
import org.recap.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by premkb on 19/8/16.
 */
@Service
public class ExportDataDumpExecutorService {

    private static final Logger logger = LoggerFactory.getLogger(ExportDataDumpExecutorService.class);

    private ExecutorService executorService;

    @Autowired
    private ProducerTemplate producer;

    @Autowired
    private BibliographicDetailsRepository bibliographicDetailsRepository;

    @Value("${data.dump.email.nypl.to}")
    String dataDumpEmailNyplTo;

    @Value("${data.dump.email.pul.to}")
    String dataDumpEmailPulTo;

    @Value("${data.dump.email.cul.to}")
    String dataDumpEmailCulTo;

    @Value("${etl.dump.directory}")
    String fileSystemDataDumpDirectory;

    @Value("${ftp.datadump.remote.server}")
    String ftpDataDumpDirectory;

    private StopWatch stopWatch;

    private int limitPage;

    public String exportDump(DataDumpRequest dataDumpRequest)throws InterruptedException,ExecutionException{
        BibRecords bibRecordsForIncremental = new BibRecords();
        bibRecordsForIncremental.setBibRecords(new ArrayList<>());
        Map<String,String> routeMap = new HashMap<>();
        Long totalRecordCount = null;
        String outputString=null;
        try {
            startProcess();
            int noOfThreads = dataDumpRequest.getNoOfThreads();
            int batchSize = dataDumpRequest.getBatchSize();
            totalRecordCount = getTotalRecordCount(dataDumpRequest);
            if(totalRecordCount == 0){
                dataDumpRequest.setRecordsAvailable(false);
            }else{
                dataDumpRequest.setRecordsAvailable(true);
            }
            String limitPageString = System.getProperty(ReCAPConstants.DATADUMP_LIMIT_PAGE);
            limitPage = System.getProperty(ReCAPConstants.DATADUMP_LIMIT_PAGE)==null ? 0 : Integer.parseInt(System.getProperty(ReCAPConstants.DATADUMP_LIMIT_PAGE));
            int loopCount = limitPageString == null ? getLoopCount(totalRecordCount,batchSize):(Integer.parseInt(limitPageString));

            if(logger.isInfoEnabled()){
                logger.info("Total no. of records "+totalRecordCount);
                int recordsToExport = 0;
                if (limitPage == 0) {
                    recordsToExport = totalRecordCount.intValue();
                } else {
                    recordsToExport = totalRecordCount > ((limitPage)*batchSize)?((limitPage)*batchSize) : totalRecordCount.intValue();
                }
                logger.info("Total no. of records to be exported based on page limit - "+recordsToExport);
                logger.info("Records per file - "+batchSize);
            }
            setExecutorService(noOfThreads);
            for(int pageNum = 0;pageNum < loopCount;pageNum++){
                StopWatch stopWatchPerFile = new StopWatch();
                stopWatchPerFile.start();
                Callable callable = getExportDataDumpCallable(pageNum,batchSize,dataDumpRequest,bibliographicDetailsRepository);
                BibRecords bibRecords = getExecutorService().submit(callable) == null ? null : (BibRecords)getExecutorService().submit(callable).get();
                String fileName = ReCAPConstants.DATA_DUMP_FILE_NAME + (pageNum+1);
                routeMap.put(ReCAPConstants.FILENAME,fileName);
                routeMap.put(ReCAPConstants.REQUESTING_INST_CODE,dataDumpRequest.getRequestingInstitutionCode());
                if (dataDumpRequest.getTransmissionType().equals(ReCAPConstants.DATADUMP_TRANSMISSION_TYPE_FTP)) {
                    producer.sendBodyAndHeader(ReCAPConstants.DATA_DUMP_FTP_Q, bibRecords, "routeMap",routeMap);
                } else if(dataDumpRequest.getTransmissionType().equals(ReCAPConstants.DATADUMP_TRANSMISSION_TYPE_FILESYSTEM)){
                    producer.sendBodyAndHeader(ReCAPConstants.DATA_DUMP_FILE_SYSTEM_Q, bibRecords, "routeMap",routeMap);
                } else {
                    bibRecordsForIncremental.getBibRecords().addAll(bibRecords.getBibRecords());
                }
                stopWatchPerFile.stop();
                if(logger.isInfoEnabled()){
                    logger.info("Total time taken to export file no. "+(pageNum+1)+" is "+stopWatchPerFile.getTotalTimeMillis()/1000+" seconds");
                    logger.info("File no. "+(pageNum+1)+" exported");
                }
            }
            if(dataDumpRequest.getTransmissionType()==ReCAPConstants.DATADUMP_TRANSMISSION_TYPE_HTTP && bibRecordsForIncremental.getBibRecords().size()>0){
                outputString = JAXBHandler.getInstance().marshal(bibRecordsForIncremental);
            }
            getExecutorService().shutdownNow();
            getStopWatch().stop();
            if(logger.isInfoEnabled()){
                logger.info("Total time taken to export all data - "+stopWatch.getTotalTimeMillis()/1000+" seconds ("+stopWatch.getTotalTimeMillis()/60000+" minutes)");
            }
            generateDataDumpReport(dataDumpRequest,totalRecordCount);
            if (dataDumpRequest.getTransmissionType().equals(0) || dataDumpRequest.getTransmissionType().equals(2)) {
                sendEmail(dataDumpRequest.getInstitutionCodes(), totalRecordCount, dataDumpRequest.getRequestingInstitutionCode(), dataDumpRequest.getTransmissionType());
            }
        } catch (IllegalStateException |InterruptedException | ExecutionException | CamelExecutionException e) {
            logger.error(e.getMessage());
            stopWatch.stop();
        }
        return outputString;
    }

    private void sendEmail(List<String> institutionCodes, Long totalRecordCount, String requestingInstitutionCode, Integer transmissionType) {
        EmailPayLoad emailPayLoad = new EmailPayLoad();
        emailPayLoad.setInstitutions(institutionCodes);
        emailPayLoad.setLocation(getLocation(transmissionType, requestingInstitutionCode));
        emailPayLoad.setCount(totalRecordCount);
        emailPayLoad.setTo(getEmailTo(requestingInstitutionCode));
        producer.sendBody(ReCAPConstants.EMAIL_Q, emailPayLoad);
    }

    private String getLocation(Integer transmissionType, String requestingInstitutionCode) {
        String location = null;
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("ddMMMyyyy");
        String day = sdf.format(date);
        if (transmissionType.equals(0)) {
            location = "FTP location - " + ftpDataDumpDirectory + File.separator + requestingInstitutionCode + File.separator + day;
        } else if (transmissionType.equals(2)) {
            location = "File System - " + fileSystemDataDumpDirectory + File.separator + requestingInstitutionCode + File.separator + day;
        }
        return location;
    }

    private String getEmailTo(String requestingInstitutionCode) {
        String emailTo = null;
        if (requestingInstitutionCode.equalsIgnoreCase(ReCAPConstants.COLUMBIA)) {
            emailTo = dataDumpEmailCulTo;
        } else if (requestingInstitutionCode.equalsIgnoreCase(ReCAPConstants.PRINCETON)) {
            emailTo = dataDumpEmailPulTo;
        } else if (requestingInstitutionCode.equalsIgnoreCase(ReCAPConstants.NYPL)) {
            emailTo = dataDumpEmailNyplTo;
        }
        return emailTo;
    }

    private void generateDataDumpReport(DataDumpRequest dataDumpRequest,Long totalRecordCount){
        Map<String,String> reportMap = new HashMap<>();
        reportMap.put(ReCAPConstants.FILENAME,"Report");
        reportMap.put(ReCAPConstants.REQUESTING_INST_CODE,dataDumpRequest.getRequestingInstitutionCode());
        String reportString = "Total no. of Bibs exported : "+totalRecordCount;
        if (dataDumpRequest.getTransmissionType().equals(ReCAPConstants.DATADUMP_TRANSMISSION_TYPE_FTP)) {
            producer.sendBodyAndHeader(ReCAPConstants.DATA_DUMP_REPORT_FTP_Q, reportString, "reportMap",reportMap);
        }else{
            producer.sendBodyAndHeader(ReCAPConstants.DATA_DUMP_REPORT_FILE_SYSTEM_Q, reportString, "reportMap",reportMap);
        }
    }

    private int getLoopCount(Long totalRecordCount,int batchSize){
        int quotient = Integer.valueOf(Long.toString(totalRecordCount)) / (batchSize);
        int remainder = Integer.valueOf(Long.toString(totalRecordCount)) % (batchSize);
        int loopCount = remainder == 0 ? quotient : quotient + 1;
        return loopCount;
    }

    private Long getTotalRecordCount(DataDumpRequest dataDumpRequest){
        Long totalRecordCount = new Long(0);
        Date inputDate = DateUtil.getDateFromString(dataDumpRequest.getDate(), ReCAPConstants.DATE_FORMAT_MMDDYYYHHMM);
        if(dataDumpRequest.getFetchType() != null){
            if(dataDumpRequest.getFetchType() == 0){
                totalRecordCount = bibliographicDetailsRepository.countByInstitutionCodes(dataDumpRequest.getCollectionGroupIds(),dataDumpRequest.getInstitutionCodes());
            }else if(dataDumpRequest.getFetchType() == 1 ){
                totalRecordCount = bibliographicDetailsRepository.countByInstitutionCodesAndLastUpdatedDate(dataDumpRequest.getCollectionGroupIds(),dataDumpRequest.getInstitutionCodes(), inputDate);
            }
        }
        logger.info("totalRecordCount----->"+totalRecordCount);
        return totalRecordCount;
    }

    private Callable getExportDataDumpCallable(int pageNum, int batchSize, DataDumpRequest dataDumpRequest, BibliographicDetailsRepository bibliographicDetailsRepository){
        return new ExportDataDumpCallable(pageNum,batchSize,dataDumpRequest,bibliographicDetailsRepository);
    }

    private void setExecutorService(Integer numThreads) {
        if (null == executorService || executorService.isShutdown()) {
            executorService = Executors.newFixedThreadPool(numThreads);
        }
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    private StopWatch getStopWatch() {
        if (null == stopWatch) {
            stopWatch = new StopWatch();
        }
        return stopWatch;
    }

    private void startProcess() {
        getStopWatch().start();
    }
}
