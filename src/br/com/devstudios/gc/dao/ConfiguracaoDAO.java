package br.com.devstudios.gc.dao;

import java.math.BigDecimal;

import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.bmp.PersistentLocalEntity;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.vo.EntityVO;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.modelcore.util.MGECoreParameter;

public class ConfiguracaoDAO {
	
	public static DynamicVO get() throws Exception {
		
		//BigDecimal idConfig = (BigDecimal) MGECoreParameter.getParameter("IDCONFIGINTAPI");
		
		EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade(); 
		PersistentLocalEntity config = dwfEntityFacade.findEntityByPrimaryKey("AD_IBOCFG", BigDecimal.ONE);
		
		EntityVO configEVO = config.getValueObject();
		DynamicVO configVO = (DynamicVO) configEVO;
		return configVO;
		
	}

}
