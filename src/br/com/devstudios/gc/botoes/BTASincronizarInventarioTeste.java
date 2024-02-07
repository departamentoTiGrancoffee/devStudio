package br.com.devstudios.gc.botoes;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Collection;
import org.json.JSONArray;
import org.json.JSONObject;
import com.sankhya.util.JdbcUtils;
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

public class BTASincronizarInventarioTeste implements AcaoRotinaJava{

	@Override
	public void doAction(ContextoAcao ctx) throws Exception {
		Registro[] registros = ctx.getLinhas();
		Registro linha = registros[0];

		BigDecimal id = (BigDecimal) linha.getCampo("ID");
		
		try {
			
			if (id != null) {
				
				JapeWrapper DAO = JapeFactory.dao("AD_ITENSCONTAGENS");
				
				JdbcWrapper jdbcWrapper = null;
				EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade();
				jdbcWrapper = dwfEntityFacade.getJdbcWrapper();
				ResultSet contagem;
				NativeSql nativeSql = new NativeSql(jdbcWrapper);
				nativeSql.resetSqlBuf();
				nativeSql.appendSql("SELECT CODPROD, DTVAL, DTFAB, CONTROLE, QTDESTOQUE, CODEMP, CODLOCAL FROM AD_ITENSCONTAGENS WHERE ID=:ID");
				nativeSql.setNamedParameter("ID", id);
				contagem = nativeSql.executeQuery();
				
				JSONArray items = new JSONArray();
				
				while (contagem.next()) {
					
					DynamicVO produtoVO = (DynamicVO) EntityFacadeFactory.getDWFFacade().findEntityByPrimaryKeyAsVO(DynamicEntityNames.PRODUTO, contagem.getBigDecimal("CODPROD"));
					DynamicVO itemProdutoVO = DAO.findOne("this.ID=? AND this.CODPROD=?", new Object[] { id,  contagem.getBigDecimal("CODPROD")});
					
					JSONObject item = new JSONObject();
					
					if (!existeCodigoDeBarras(contagem.getBigDecimal("CODPROD"))) {
						DAO.prepareToUpdate(itemProdutoVO).set("STATUS", "F").set("LOG", "PRODUTO SEM CODIGO DE BARRAS").update();
					}else {
						item.put("validation_date", contagem.getTimestamp("DTVAL"));
						item.put("fabrication_date", contagem.getTimestamp("DTFAB"));
						item.put("control", contagem.getString("CONTROLE"));
						item.put("quantity", contagem.getBigDecimal("QTDESTOQUE"));
						item.put("cost_value", getCustoProduto(contagem.getBigDecimal("CODPROD"), contagem.getBigDecimal("CODEMP")));
						item.put("product_id", contagem.getBigDecimal("CODPROD"));
						item.put("stock_location_id", contagem.getBigDecimal("CODLOCAL"));
						item.put("unit_id", produtoVO.asString("CODVOL"));
						
						items.put(item);
					}
					
				}

				ctx.setMensagemRetorno("QTD DE ITENS INSERIDOS NA LISTA: "+items.length()+"\n ARRAY JSON: "+items.toString());
						
				
			}
			
		} catch (Exception e1) {
			e1.printStackTrace();
			ctx.mostraErro(e1.getMessage());
		}
		
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

}
