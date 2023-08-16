package org.recap.model.jparw;

import lombok.Data;

import org.recap.model.jpa.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import javax.validation.constraints.Email;
import java.util.Date;

@Entity
@Table(name = "ETL_REQUEST_LOG_T", catalog = "")
@Data
@AttributeOverride(
        name = "id",
        column = @Column(
                name = "ETL_REQUEST_ID"
        )
)
public class ETLRequestLogEntity extends AbstractEntity<Integer> {

    @Email
    @Column(name="EMAIL_IDS")
    private String emailIds;

    @Column(name = "COLLECTION_GROUP_IDS")
    private String collectionGroupIds;

    @Column(name="FETCH_TYPE")
    private String fetchType;

    @Column(name="OUTPUT_FORMAT")
    private String outputFormat;

    @Column(name = "IMS_REPOSITORY_CODES")
    private String imsRepositoryCodes;

    @Column(name = "REQUESTING_INST_CODE")
    private String requestingInstCode;

    @Column(name = "INST_CODE_TO_EXP")
    private String instCodeToExport;

    @Column(name="TRANSMISSION_TYPE")
    private String transmissionType;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "DATE_PROVIDED")
    private Date providedDate;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "REQUESTED_TIME")
    private Date requestedTime;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "COMPLETED_TIME")
    private Date completeTime;

    @Column(name="EXPORT_STATUS_ID")
    private Integer exportStatusId;

    @ManyToOne
    @JoinColumn(
            name = "EXPORT_STATUS_ID",
            insertable = false,
            updatable = false
    )
    private ExportStatusEntity exportStatusEntity;

    @Column(name="MESSAGE")
    private String message;

    @Column(name="USER_NAME")
    private String userName;

}
