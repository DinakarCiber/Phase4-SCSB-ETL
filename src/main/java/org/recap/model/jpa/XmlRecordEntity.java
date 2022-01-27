package org.recap.model.jpa;

import lombok.Data;


import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;

/**
 * Created by peris on 7/17/16.
 */
@Entity
@Table(name = "xml_records_t", catalog = "")
@Data
public class XmlRecordEntity extends AbstractEntity<Integer> {
    @Lob
    @Column(name = "xml_record")
    private byte[] xml;

    @Column(name = "xml_file")
    private String xmlFileName;

    @Column(name="owning_inst")
    private String owningInst;

    @Column(name="owning_inst_bib_id")
    private String owningInstBibId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date_loaded")
    private Date dataLoaded;
}
