package ${project.packageName}.service;

import ${project.packageName}.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ${module.serviceName} {

<#list module.endpoints as ep>
    ${ep.returnType} ${ep.methodName}(<#if ep.paged>Pageable pageable<#else><#if ep.hasPathVariable>${entity.idType} id<#if ep.hasRequestBody>, </#if></#if><#if ep.hasRequestBody>${ep.requestBodyType} request</#if></#if>);
</#list>
}
