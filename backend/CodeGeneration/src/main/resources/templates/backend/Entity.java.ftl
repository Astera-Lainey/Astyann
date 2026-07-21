package ${project.packageName}.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "${entity.tableName}")
@Getter
@Setter
@NoArgsConstructor
<#if entity.audited>@EntityListeners(AuditingEntityListener.class)
</#if>public class ${entity.className} {

    @Id
<#if entity.idStrategy == "IDENTITY">
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
<#else>
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
</#if>

<#list entity.fields as field>
    @Column(name = "${field.columnName}"<#if field.unique>, unique = true</#if><#if field.required>, nullable = false</#if>)
<#if field.required>
    @NotNull
</#if>
<#if (field.minLength)?? || (field.maxLength)??>
    @Size(<#if (field.minLength)??>min = ${field.minLength?c}<#if (field.maxLength)??>, </#if></#if><#if (field.maxLength)??>max = ${field.maxLength?c}</#if>)
</#if>
    private ${field.javaType} ${field.name};

</#list>
<#list entity.relationships as rel>
<#if rel.relationType == "MANY_TO_ONE">
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "${rel.joinColumn}")
    private ${rel.targetEntity} ${rel.fieldName};

<#elseif rel.relationType == "ONE_TO_ONE">
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "${rel.joinColumn}")
    private ${rel.targetEntity} ${rel.fieldName};

<#elseif rel.relationType == "ONE_TO_MANY">
    @OneToMany(fetch = FetchType.LAZY)
    private List<${rel.targetEntity}> ${rel.fieldName} = new ArrayList<>();

<#else>
    @ManyToMany
    private List<${rel.targetEntity}> ${rel.fieldName} = new ArrayList<>();

</#if>
</#list>
<#if entity.audited>
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "last_modified_at")
    private LocalDateTime lastModifiedAt;
</#if>
}
