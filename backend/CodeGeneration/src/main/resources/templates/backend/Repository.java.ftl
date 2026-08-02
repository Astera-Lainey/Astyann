package ${project.packageName}.repository;

import ${project.packageName}.entity.${entity.className};
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ${entity.className}Repository extends JpaRepository<${entity.className}, ${entity.idType}> {
<#list entity.fields as field>
<#if field.name == "active" || field.name == "archived">
    List<${entity.className}> findBy${field.name?cap_first}(boolean ${field.name});
</#if>
</#list>
}
