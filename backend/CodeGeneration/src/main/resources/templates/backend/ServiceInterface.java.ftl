package ${project.packageName}.service;

import ${project.packageName}.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ${module.serviceName} {

<#list module.endpoints as ep>
<#assign pathParams><#list ep.pathVariables as pv>${entity.idType} ${pv}<#sep>, </#sep></#list></#assign>
    ${ep.returnType} ${ep.methodName}(<#if ep.paged>Pageable pageable<#else>${pathParams}<#if ep.pathVariables?has_content && ep.hasRequestBody>, </#if><#if ep.hasRequestBody>${ep.requestBodyType} request</#if></#if>);
</#list>
}
