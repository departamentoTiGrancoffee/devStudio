package br.com.devstudios.gc.botoes;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import org.json.JSONArray;
import org.json.JSONObject;
import com.sankhya.util.JdbcUtils;
import com.sankhya.util.TimeUtils;
import br.com.devstudios.gc.dao.ConfiguracaoDAO;
import br.com.devstudios.gc.services.CallService;
import br.com.devstudios.gc.services.ProdutoService;
import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava;
import br.com.sankhya.extensions.actionbutton.ContextoAcao;
import br.com.sankhya.extensions.actionbutton.Registro;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.bmp.PersistentLocalEntity;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.vo.EntityVO;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;

public class BTASincronizarInventario implements AcaoRotinaJava {
	
	private  static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	@Override
	public void doAction(ContextoAcao ctx) throws Exception {
		
		Registro[] registros = ctx.getLinhas();
		Registro linha = registros[0];
		
		if(!(linha.getCampo("STATUS") == null)) {
			
			if(linha.getCampo("STATUS").equals("2")) {
				ctx.mostraErro("Inventário em andamento, não pode ser alterado!");
				return;
			}
			
			if(linha.getCampo("STATUS").equals("3")) {
				ctx.mostraErro("Inventário finalizado, não pode ser alterado!");
				return;
			}
		}
		
		JdbcWrapper jdbc = null; 
		EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade(); 
		jdbc = dwfEntityFacade.getJdbcWrapper();
		NativeSql sql = (NativeSql) null;
		ResultSet rs = (ResultSet) null;
		try {
			String query = "SELECT ITE.*, PRO.CODVOL, ("
					+ "SELECT CUSTO FROM AD_CUSTOPROD_INVENT cus WHERE "
					+ " cus.codprod=ITE.codprod and ITE.codemp=cus.empresa "
					+ " ) AS CUSTO "
					+ " FROM AD_ITENSCONTAGENS ite "
					+ " INNER JOIN TGFPRO PRO ON PRO.CODPROD=ITE.CODPROD"
					+ " WHERE ite.DTCONTAGEM='"+TimeUtils.clearTime((Timestamp) linha.getCampo("DTCONTAGEM")).toLocalDateTime().format(FORMATTER)+"'"
					+ " AND ite.CODEMP="+linha.getCampo("CODEMP")
					+ " AND ite.CODLOCAL="+linha.getCampo("CODLOCAL");
			sql = new NativeSql(jdbc);
			sql.appendSql(query);
			rs = sql.executeQuery();
			
			JSONArray items = new JSONArray();
			
			//BigDecimal codLocal = new BigDecimal(linha.getCampo("CODLOCAL").toString());
			
			while (rs.next()) {
				JSONObject item = new JSONObject();
				
				DynamicVO produtoVO = (DynamicVO) EntityFacadeFactory.getDWFFacade().findEntityByPrimaryKeyAsVO(
						DynamicEntityNames.PRODUTO, rs.getBigDecimal("CODPROD"));
				
				PersistentLocalEntity itemCopia = dwfEntityFacade.findEntityByPrimaryKey("AD_ITENSCONTAGENS", new Object[] {
						linha.getCampo("ID"),
						rs.getBigDecimal("CODPROD"),
						linha.getCampo("CODLOCAL"),
						linha.getCampo("CODEMP"),
						linha.getCampo("DTCONTAGEM"),
						rs.getString("CONTROLE")
						
						});
				EntityVO itemEVO = itemCopia.getValueObject();
				DynamicVO itemVO = (DynamicVO) itemEVO;
				
				if(!(jaCodigoDeBarras(rs.getBigDecimal("CODPROD")))) {
					
					itemVO.setProperty("STATUS", "F");
					itemVO.setProperty("LOG", "PRODUTO SEM CODIGO DE BARRAS");
					
				}else {
					
					ProdutoService.handle(produtoVO);
					
					Thread.sleep(1000);
					
					item.put("validation_date", rs.getDate("DTVAL"));
					item.put("fabrication_date", rs.getDate("DTFAB"));
					item.put("control", rs.getString("CONTROLE"));
					item.put("quantity", rs.getBigDecimal("QTDESTOQUE"));
					item.put("cost_value", rs.getBigDecimal("CUSTO"));
					//item.put("type", rs.getString("TIPO").trim());
					item.put("product_id", rs.getBigDecimal("CODPROD"));
					item.put("stock_location_id", rs.getBigDecimal("CODLOCAL"));
					item.put("unit_id", rs.getString("CODVOL"));
					
					items.put(item);
					
					itemVO.setProperty("STATUS", "E");
					itemVO.setProperty("LOG", "");
					
				}
				
				itemCopia.setValueObject(itemEVO);

			}
			
			if(items.length() > 0) {
				DynamicVO configVO = ConfiguracaoDAO.get();
				String uri = configVO.asString("URL");
				CallService service = new CallService();
				
				JSONObject body = new JSONObject();
				
				DynamicVO empresaVO = (DynamicVO) EntityFacadeFactory.getDWFFacade().findEntityByPrimaryKeyAsVO(
						DynamicEntityNames.EMPRESA, linha.getCampo("CODEMP"));
				
				if(empresaVO.asString("AD_USALOTEAPPINV") == null) {
					body.put("validate_lote", false);
				}else if(empresaVO.asString("AD_USALOTEAPPINV").equals("S")) {
					body.put("validate_lote", true);
				}else {
					body.put("validate_lote", false);
				}
				
				body.put("id", linha.getCampo("ID"));
				body.put("company_id", linha.getCampo("CODEMP"));
				body.put("location", linha.getCampo("CODLOCAL"));
				body.put("counting_date", TimeUtils.clearTime((Timestamp) linha.getCampo("DTCONTAGEM")).toLocalDateTime().format(FORMATTER));
				body.put("items",items);
				service.setBody(body.toString());
				service.setMethod("POST");
				service.setUri(uri + "/inventories");
				
				String response = service.fire();
				
				JSONObject respObject = new JSONObject(response);
				
				int idResponse = respObject.getInt("id");
				
				Date dateobj = new Date();
				linha.setCampo("CODUSU", ctx.getUsuarioLogado());
				linha.setCampo("DTENVIO", new Timestamp(dateobj.getTime()));
				//linha.setCampo("ID", String.valueOf(idResponse));
				linha.setCampo("STATUS", "1");
				
				ctx.setMensagemRetorno("Processo concluído!<br>"+items.length()+" itens enviado(s)!");
			}else {
				ctx.setMensagemRetorno("Não foram encontrados itens de contagem para o filtro selecionado!");
			}
		
		} catch (Exception e1) {
			e1.printStackTrace();
			ctx.mostraErro(e1.getMessage());
		} finally {
			JdbcUtils.closeResultSet(rs);
			NativeSql.releaseResources(sql);
		}
	}
	
	private boolean jaCodigoDeBarras(BigDecimal codprod)  {
		JdbcWrapper jdbc = null; 
		EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade(); 
		jdbc = dwfEntityFacade.getJdbcWrapper();
		NativeSql sql = null;
		ResultSet rs = null;
		try {
			sql = new NativeSql(jdbc);
			sql.appendSql("select 1 from AD_BARINVENT where CODPROD = "+codprod);
			rs = sql.executeQuery();
			if(rs.next()) {
				return true;
			}
		} catch (Exception e1) {
			e1.printStackTrace();
		} finally {
			JdbcUtils.closeResultSet(rs);
			NativeSql.releaseResources(sql);
		}
		return false;
	}
}