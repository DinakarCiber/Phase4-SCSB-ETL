package org.recap.service;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.recap.RecapCommonConstants;
import org.recap.RecapConstants;
import org.recap.model.ILSConfigProperties;
import org.recap.model.export.DataDumpRequest;
import org.recap.repository.ImsLocationDetailsRepository;
import org.recap.repository.InstitutionDetailsRepository;
import org.recap.util.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DataExportValidateService {

    private static final Logger logger = LoggerFactory.getLogger(DataExportValidateService.class);

    @Value("${etl.data.dump.fetchtype.full}") private String fetchTypeFull;
    @Value("${etl.data.dump.status.file.name}") private String dataDumpStatusFileName;
    @Value("${etl.data.dump.incremental.date.limit}") private String incrementalDateLimit;

    @Autowired private InstitutionDetailsRepository institutionDetailsRepository;
    @Autowired private PropertyUtil propertyUtil;
    @Autowired private ImsLocationDetailsRepository imsLocationDetailsRepository;

    /**
     * Validate incoming data dump request.
     *
     * @param dataDumpRequest the data dump request
     * @return the string
     */
    public String validateIncomingRequest(DataDumpRequest dataDumpRequest) {
        String validationMessage = null;
        Date currentDate = new Date();
        Map<Integer, String> errorMessageMap = new HashMap<>();
        Integer errorcount = 1;
        List<String> allInstitutionCodeExceptHTC = institutionDetailsRepository.findAllInstitutionCodeExceptHTC();
        if (!dataDumpRequest.getInstitutionCodes().isEmpty()) {
            for (String institutionCode : dataDumpRequest.getInstitutionCodes()) {
                if(!allInstitutionCodeExceptHTC.contains(institutionCode)){
                    errorMessageMap.put(errorcount, RecapConstants.DATADUMP_VALID_INST_CODES_ERR_MSG+" : "+propertyUtil.getAllInstitutions().toString());
                    errorcount++;
                }
            }
            if(dataDumpRequest.getInstitutionCodes().size() != 1 && dataDumpRequest.getFetchType().equals(fetchTypeFull)) {
                errorMessageMap.put(errorcount, RecapConstants.DATADUMP_MULTIPLE_INST_CODES_ERR_MSG+ " : "+propertyUtil.getAllInstitutions().toString());
                errorcount++;
            }
        }
        if(dataDumpRequest.getRequestingInstitutionCode() != null && !allInstitutionCodeExceptHTC.contains(dataDumpRequest.getRequestingInstitutionCode())){
            errorMessageMap.put(errorcount, RecapConstants.DATADUMP_VALID_REQ_INST_CODE_ERR_MSG+" : "+propertyUtil.getAllInstitutions().toString());
            errorcount++;
        }
        List<String> imsLocationCodes = imsLocationDetailsRepository.findAllImsLocationCodeExceptUnknown();
        for (String imsDepositoryCode : dataDumpRequest.getImsDepositoryCodes()){
            if(!imsLocationCodes.contains(imsDepositoryCode)){
                errorMessageMap.put(errorcount, RecapConstants.DATADUMP_VALID_IMS_DEPOSITORY_CODE_ERR_MSG);
                errorcount++;
                break;
            }
        }
        if (!dataDumpRequest.getFetchType().equals(fetchTypeFull) &&
                !dataDumpRequest.getFetchType().equals(RecapConstants.DATADUMP_FETCHTYPE_INCREMENTAL)
                && !dataDumpRequest.getFetchType().equals(RecapConstants.DATADUMP_FETCHTYPE_DELETED)) {
            errorMessageMap.put(errorcount, RecapConstants.DATADUMP_VALID_FETCHTYPE_ERR_MSG);
            errorcount++;
        }
        if (!dataDumpRequest.getTransmissionType().equals(RecapConstants.DATADUMP_TRANSMISSION_TYPE_S3)
                && !dataDumpRequest.getTransmissionType().equals(RecapConstants.DATADUMP_TRANSMISSION_TYPE_HTTP)
        ) {
            errorMessageMap.put(errorcount, RecapConstants.DATADUMP_TRANS_TYPE_ERR_MSG);
            errorcount++;
        }
        if (dataDumpRequest.getFetchType().equals(fetchTypeFull) && dataDumpRequest.getInstitutionCodes() == null) {
            errorMessageMap.put(errorcount, RecapConstants.DATADUMP_INSTITUTIONCODE_ERR_MSG);
            errorcount++;
        }
        if (dataDumpRequest.getFetchType().equals(RecapConstants.DATADUMP_FETCHTYPE_INCREMENTAL) && dataDumpRequest.getDate() == null || "".equals(dataDumpRequest.getDate())) {
            errorMessageMap.put(errorcount, RecapConstants.DATADUMP_DATE_ERR_MSG);
            errorcount++;
        }
        if(dataDumpRequest.getFetchType().equals(RecapConstants.DATADUMP_FETCHTYPE_INCREMENTAL) || dataDumpRequest.getFetchType().equals(RecapConstants.DATADUMP_FETCHTYPE_DELETED)) {
            String dataDumpRequestDateString = dataDumpRequest.getDate();
            List<String> institutionCodes = dataDumpRequest.getInstitutionCodes();
            if(StringUtils.isNotBlank(dataDumpRequestDateString)) {
                try {
                    boolean isValidDate = validateDate(dataDumpRequestDateString);
                    for(String imsLocationCode : imsLocationCodes) {
                        if(isValidDate) {
                            for (String institutionCode : institutionCodes) {
                                ILSConfigProperties ilsConfigProperties = propertyUtil.getILSConfigProperties(institutionCode);
                                errorcount = checkToRestrictFullDumpViaIncremental(errorMessageMap, errorcount, dataDumpRequestDateString, ilsConfigProperties.getEtlInitialDataLoadedDate(), institutionCode, imsLocationCode);
                            }

                            errorcount = checkForIncrementalDateLimit(currentDate, errorMessageMap, errorcount, dataDumpRequestDateString, imsLocationCode);
                        } else {
                            errorMessageMap.put(errorcount, MessageFormat.format(RecapConstants.INVALID_DATE_FORMAT, RecapCommonConstants.DATE_FORMAT_YYYYMMDDHHMM));
                            errorcount++;
                        }
                    }

                } catch (Exception e) {
                    logger.error("Exception : ", e);
                }
            }
        }
        if (dataDumpRequest.getTransmissionType().equals(RecapConstants.DATADUMP_TRANSMISSION_TYPE_S3)) {
            if (StringUtils.isEmpty(dataDumpRequest.getToEmailAddress())) {
                errorMessageMap.put(errorcount, RecapConstants.DATADUMP_EMAIL_TO_ADDRESS_REQUIRED);
                errorcount++;
            } else {
                boolean isValid = validateEmailAddress(dataDumpRequest.getToEmailAddress());
                if (!isValid) {
                    errorMessageMap.put(errorcount, RecapConstants.INVALID_EMAIL_ADDRESS);
                    errorcount++;
                }
            }
        }
        if(RecapConstants.DATADUMP_TYPES.contains(dataDumpRequest.getFetchType())&& dataDumpRequest.getTransmissionType().equals(RecapConstants.DATADUMP_TRANSMISSION_TYPE_S3) && !dataDumpRequest.isRequestFromSwagger()) {
            String dataExportStatus = getDataExportCurrentStatus();
            String status = Optional.ofNullable(dataExportStatus).orElse("No file created");
            logger.info("Validating datadump status file for requested Dump-Type {} by {} . Status : {}",dataDumpRequest.getFetchType(),dataDumpRequest.getRequestingInstitutionCode(),status);
            if(dataExportStatus != null && dataExportStatus.contains(RecapConstants.IN_PROGRESS)){
                errorMessageMap.put(errorcount, RecapConstants.INPROGRESS_ERR_MSG);
                errorcount++;
            }
        }
        if (errorMessageMap.size() > 0) {
            validationMessage = buildErrorMessage(errorMessageMap);
        }
        return validationMessage;
    }

    private boolean validateDate(String dataDumpRequestDateString) {
        String[] dateStringArray = dataDumpRequestDateString.split(" ");
        if(dateStringArray.length == 1) {
            return false;
        } else {
            Date formattedDate = getFormattedDate(RecapCommonConstants.DATE_FORMAT_YYYYMMDDHHMM, dataDumpRequestDateString);
            if(formattedDate == null) {
                return false;
            }
        }
        return true;
    }

    private Date getFormattedDate(String dateFormat, String dateString) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormat);
        try {
            return simpleDateFormat.parse(dateString);
        } catch (ParseException e) {
            logger.error("Exception while Parsing Date : ", e);
        }
        return null;
    }

    /**
     * Builds error message string from map.
     * @param erroMessageMap
     * @return
     */
    private String buildErrorMessage(Map<Integer, String> erroMessageMap) {
        StringBuilder errorMessageBuilder = new StringBuilder();
        erroMessageMap.forEach((key, value) -> errorMessageBuilder.append(key).append(". ").append(value).append("\n"));
        return errorMessageBuilder.toString();
    }

    /**
     * Gets the data dump export status reading from status file.
     * @return
     */
    private String getDataExportCurrentStatus(){
        File file = new File(dataDumpStatusFileName);
        String dataDumpStatus = null;
        try {
            if (file.exists()) {
                dataDumpStatus = FileUtils.readFileToString(file, Charset.defaultCharset());
            }
        } catch (IOException e) {
            logger.error(RecapConstants.ERROR,e);
            logger.error("Exception while creating or updating the file : " + e.getMessage());
        }
        return dataDumpStatus;
    }

    /**
     * Validates email address.
     * @param toEmailAddress
     * @return
     */
    private boolean validateEmailAddress(String toEmailAddress) {
        String regex = RecapCommonConstants.REGEX_FOR_EMAIL_ADDRESS;
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(toEmailAddress);
        return matcher.matches();
    }

    private Integer checkToRestrictFullDumpViaIncremental(Map<Integer, String> errorMessageMap, Integer errorcount, String dataDumpRequestDateString, String initialDataLoadDateString, String institutionCode, String imsLocationCode) {
        if(StringUtils.isBlank(initialDataLoadDateString)) {
            errorMessageMap.put(errorcount, MessageFormat.format(RecapConstants.INITIAL_DATA_LOAD_DATE_MISSING_ERR_MSG, institutionCode, propertyUtil.getPropertyByImsLocationAndKey(imsLocationCode, "las.email.assist.to")));
            errorcount++;
        } else {
            Date dataDumpRequestDate = getFormattedDate(RecapConstants.DATE_FORMAT_YYYYMMDD, dataDumpRequestDateString);
            Date initialDataLoadDate = getFormattedDate(RecapConstants.DATE_FORMAT_YYYYMMDD, initialDataLoadDateString);
            if(initialDataLoadDate.after(dataDumpRequestDate) || initialDataLoadDate.equals(dataDumpRequestDate)) {
                errorMessageMap.put(errorcount, MessageFormat.format(RecapConstants.RESTRICT_FULLDUMP_VIA_INCREMENTAL_ERROR_MSG, institutionCode, propertyUtil.getPropertyByImsLocationAndKey(imsLocationCode, "las.email.assist.to")));
                errorcount++;
            }
        }
        return errorcount;
    }

    private Integer checkForIncrementalDateLimit(Date currentDate, Map<Integer, String> errorMessageMap, Integer errorcount, String dataDumpRequestDateString, String imsLocationCode) {
        Date dataDumpRequestDateTime = getFormattedDate(RecapCommonConstants.DATE_FORMAT_YYYYMMDDHHMM, dataDumpRequestDateString);
        long dateDifference = currentDate.getTime() - dataDumpRequestDateTime.getTime();
        long days = TimeUnit.DAYS.convert(dateDifference, TimeUnit.MILLISECONDS);

        if(StringUtils.isBlank(incrementalDateLimit)) {
            errorMessageMap.put(errorcount, MessageFormat.format(RecapConstants.INCREMENTAL_DATE_LIMIT_EMPTY_ERR_MSG, propertyUtil.getPropertyByImsLocationAndKey(imsLocationCode, "las.email.assist.to")));
            errorcount++;
        } else {
            if(Math.toIntExact(days) > Integer.valueOf(incrementalDateLimit)) {
                errorMessageMap.put(errorcount, MessageFormat.format(RecapConstants.DATADUMP_DAYS_LIMIT_EXCEEDED_ERROR_MSG, incrementalDateLimit, propertyUtil.getPropertyByImsLocationAndKey(imsLocationCode, "las.email.assist.to")));
                errorcount++;
            }
        }
        return errorcount;
    }

}
