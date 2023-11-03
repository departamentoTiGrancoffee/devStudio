package br.com.devstudios.gc.botoes;

import java.sql.ResultSet;

import org.json.JSONObject;

import com.sankhya.util.JdbcUtils;

import br.com.devstudios.gc.dao.ConfiguracaoDAO;
import br.com.devstudios.gc.services.CallService;
import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava;
import br.com.sankhya.extensions.actionbutton.ContextoAcao;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;


public class BTASincronizarEmpresas implements AcaoRotinaJava {

	@Override
	public void doAction(ContextoAcao ctx) throws Exception {
		JdbcWrapper jdbc = null; 
		EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade(); 
		jdbc = dwfEntityFacade.getJdbcWrapper();
		NativeSql sql = (NativeSql) null;
		ResultSet rs = (ResultSet) null;
		int cadEnv = 0;
		try {
			sql = new NativeSql(jdbc);
			sql.appendSql("SELECT * FROM TSIEMP");
			rs = sql.executeQuery();
			while (rs.next()) {
				DynamicVO configVO = ConfiguracaoDAO.get();
				String uri = configVO.asString("URL");
				CallService service = new CallService();
				
				JSONObject body = new JSONObject();
				body.put("id", rs.getString("CODEMP"));
				body.put("name", rs.getString("RAZAOSOCIAL"));
				body.put("validate_lote", rs.getString("AD_USALOTEAPPINV") == null ? 0 : rs.getString("AD_USALOTEAPPINV").equals("S") ? 1 : 0);
				service.setBody(body.toString());
				
				service.setMethod("POST");
				service.setUri(uri + "/companies");
				
				service.fire();
				cadEnv++;
			}
			ctx.setMensagemRetorno("Processo concluído!<br>"+cadEnv+" cadastros enviado(s)!");
		} catch (Exception e1) {
			e1.printStackTrace();
			ctx.mostraErro(e1.getMessage());
		} finally {
			JdbcUtils.closeResultSet(rs);
			NativeSql.releaseResources(sql);
		}
	}
}