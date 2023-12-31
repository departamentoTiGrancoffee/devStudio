package br.com.devstudios.gc.botoes;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

import org.json.JSONArray;
import org.json.JSONObject;

import com.sankhya.util.JdbcUtils;
import com.sankhya.util.TimeUtils;

import br.com.devstudios.gc.dao.ConfiguracaoDAO;
import br.com.devstudios.gc.services.CallService;
import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava;
import br.com.sankhya.extensions.actionbutton.ContextoAcao;
import br.com.sankhya.extensions.actionbutton.Registro;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;

public class BTASincronizarInventarioNovo implements AcaoRotinaJava {

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	@Override
	public void doAction(ContextoAcao ctx) throws Exception {

		Registro[] registros = ctx.getLinhas();
		Registro linha = registros[0];

		if (!(linha.getCampo("STATUS") == null)) {

			if (linha.getCampo("STATUS").equals("2")) {
				ctx.mostraErro("Inventário em andamento, não pode ser alterado!");
				return;
			}

			if (linha.getCampo("STATUS").equals("3")) {
				ctx.mostraErro("Inventário finalizado, não pode ser alterado!");
				return;
			}
		}

		BigDecimal id = (BigDecimal) linha.getCampo("ID");
		
		try {
			
			if (id != null) {
				JapeWrapper DAO = JapeFactory.dao("AD_ITENSCONTAGENS");
				Collection<DynamicVO> listaItens = DAO.find("this.ID=?", new Object[] { id });

				JSONArray items = new JSONArray();

				for (DynamicVO i : listaItens) {
					JSONObject item = new JSONObject();
					DynamicVO produtoVO = (DynamicVO) EntityFacadeFactory.getDWFFacade()
							.findEntityByPrimaryKeyAsVO(DynamicEntityNames.PRODUTO, i.asBigDecimal("CODPROD"));

					if (!existeCodigoDeBarras(i.asBigDecimal("CODPROD"))) {
						DAO.prepareToUpdate(i).set("STATUS", "F").set("LOG", "PRODUTO SEM CODIGO DE BARRAS").update();
					} else {
						item.put("validation_date", i.asTimestamp("DTVAL"));
						item.put("fabrication_date", i.asTimestamp("DTFAB"));
						item.put("control", i.asString("CONTROLE"));
						item.put("quantity", i.asBigDecimal("QTDESTOQUE"));
						item.put("cost_value", getCustoProduto(i.asBigDecimal("CODPROD"), i.asBigDecimal("CODEMP")));
						item.put("product_id", i.asBigDecimal("CODPROD"));
						item.put("stock_location_id", i.asBigDecimal("CODLOCAL"));
						item.put("unit_id", produtoVO.asString("CODVOL"));
						
						items.put(item);
						
						DAO.prepareToUpdate(i).set("STATUS", "E").set("LOG", "PRODUTO ENVIADO COM SUCESSO").update();
					}
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
									
					linha.setCampo("CODUSU", ctx.getUsuarioLogado());
					linha.setCampo("DTENVIO", TimeUtils.getNow());
					linha.setCampo("STATUS", "1");
					
					service.fire();
					
//					ctx.setMensagemRetorno(
//					"<b>URL:</b> "+uri + "/inventories"+
//					"<br/><b>Body:</b> "+body.toString()+
//					"<br/><b>Resposta</b> "+service.fire());
					ctx.setMensagemRetorno("Processo concluído!<br>"+items.length()+" itens enviado(s)!");
				}else {
					ctx.setMensagemRetorno("Não foram encontrados itens de contagem para o filtro selecionado!");
				}
				
			}
			
		} catch (Exception e1) {
			e1.printStackTrace();
			ctx.mostraErro(e1.getMessage());
		}
	
	}
	
	private BigDecimal getCustoProduto(BigDecimal produto, BigDecimal empresa) throws Exception {
		BigDecimal custo = BigDecimal.ZERO;
		
		JdbcWrapper jdbcWrapper = null;
		EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade();
		jdbcWrapper = dwfEntityFacade.getJdbcWrapper();
		ResultSet contagem;
		NativeSql nativeSql = new NativeSql(jdbcWrapper);
		nativeSql.resetSqlBuf();
		nativeSql.appendSql("SELECT CUSTO FROM AD_CUSTOPROD_INVENT CUS WHERE CUS.CODPROD=:PRODUTO AND CUS.EMPRESA=:EMPRESA");
		nativeSql.setNamedParameter("PRODUTO", produto);
		nativeSql.setNamedParameter("EMPRESA", empresa);
		contagem = nativeSql.executeQuery();
		while (contagem.next()) {
			custo = contagem.getBigDecimal("CUSTO");
		}
		
		JdbcUtils.closeResultSet(contagem);
		NativeSql.releaseResources(nativeSql);
		
		return custo;
	}

	private boolean existeCodigoDeBarras(BigDecimal codprod) {
		JdbcWrapper jdbc = null;
		EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade();
		jdbc = dwfEntityFacade.getJdbcWrapper();
		NativeSql sql = null;
		ResultSet rs = null;
		try {
			sql = new NativeSql(jdbc);
			sql.appendSql("select 1 from AD_BARINVENT where CODPROD = " + codprod);
			rs = sql.executeQuery();
			if (rs.next()) {
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