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
import br.com.sankhya.jape.bmp.PersistentLocalEntity;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.util.FinderWrapper;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.vo.EntityVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;

public class BTASincronizarInventarioNovo implements AcaoRotinaJava {

	/**
	 * 01-02-2024 - Gabriel Nascimento - Alterado o método de verificação dos registros (JapeWrapper.find) pois estava trazendo apenas 200, alteramos para  o NativeSql.
	 * 20-03-2024 - Lucas Santana - Inserido método sincronizaProds, que sincroniza o produto no momento da sincronização do inventário.
	 */

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	@Override
	public void doAction(ContextoAcao ctx) throws Exception {

		Registro[] registros = ctx.getLinhas();
		Registro linha = registros[0];

		String status = (String) linha.getCampo("STATUS");

		if (status != null && !status.isEmpty()) {
			if (!status.equals("1")) {
				ctx.mostraErro("Inventário em andamento ou finalizado, não pode ser transmitido para o portal!");
				return;
			}
		}

		BigDecimal id = (BigDecimal) linha.getCampo("ID");

		try {

			if (id != null) {
				JapeWrapper DAO = JapeFactory.dao("AD_ITENSCONTAGENS");
				DynamicVO empresaVO = (DynamicVO) EntityFacadeFactory.getDWFFacade()
						.findEntityByPrimaryKeyAsVO(DynamicEntityNames.EMPRESA, linha.getCampo("CODEMP"));

				JdbcWrapper jdbcWrapper = null;
				EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade();
				jdbcWrapper = dwfEntityFacade.getJdbcWrapper();
				ResultSet contagem;
				NativeSql nativeSql = new NativeSql(jdbcWrapper);
				nativeSql.resetSqlBuf();
				nativeSql.appendSql(
						"SELECT CODPROD, DTVAL, DTFAB, CONTROLE, QTDESTOQUE, CODEMP, CODLOCAL FROM AD_ITENSCONTAGENS WHERE ID=:ID");
				nativeSql.setNamedParameter("ID", id);
				contagem = nativeSql.executeQuery();

				JSONArray items = new JSONArray();

				while (contagem.next()) {
					DynamicVO produtoVO = (DynamicVO) EntityFacadeFactory.getDWFFacade()
							.findEntityByPrimaryKeyAsVO(DynamicEntityNames.PRODUTO, contagem.getBigDecimal("CODPROD"));
					
					String controle = contagem.getString("CONTROLE").trim();
					DynamicVO itemProdutoVO = null;
					
					if(!controle.isEmpty()) {
						itemProdutoVO = DAO.findOne("this.ID=? AND this.CODPROD=? AND this.CONTROLE=?", new Object[] { id, contagem.getBigDecimal("CODPROD"), controle });
					}else {
						itemProdutoVO = DAO.findOne("this.ID=? AND this.CODPROD=?", new Object[] { id, contagem.getBigDecimal("CODPROD")});
						
					}					

					JSONObject item = new JSONObject();			

					if (!existeCodigoDeBarras(contagem.getBigDecimal("CODPROD"))) {
						DAO.prepareToUpdate(itemProdutoVO).set("STATUS", "F").set("LOG", "PRODUTO SEM CODIGO DE BARRAS OU SEM CÓDIGO DE BARRAS PARA A UNIDADE PADRÃO.")
								.update();
					} else {
						item.put("validation_date", contagem.getTimestamp("DTVAL"));
						item.put("fabrication_date", contagem.getTimestamp("DTFAB"));
						item.put("control", contagem.getString("CONTROLE"));
						item.put("quantity", contagem.getBigDecimal("QTDESTOQUE"));
						item.put("cost_value",
								getCustoProduto(contagem.getBigDecimal("CODPROD"), contagem.getBigDecimal("CODEMP")));
						item.put("product_id", contagem.getBigDecimal("CODPROD"));
						item.put("stock_location_id", contagem.getBigDecimal("CODLOCAL"));
						item.put("unit_id", produtoVO.asString("CODVOL"));
						item.put("validate_lote", verificaSeValidaLote(empresaVO.asBigDecimal("CODEMP"),
								contagem.getBigDecimal("CODPROD")));

						items.put(item);

						DAO.prepareToUpdate(itemProdutoVO).set("STATUS", "E").set("LOG", "PRODUTO ENVIADO COM SUCESSO").update();

						sincronizaProds(contagem.getBigDecimal("CODPROD"));
					}
				}

				JdbcUtils.closeResultSet(contagem);
				NativeSql.releaseResources(nativeSql);

				if (items.length() > 0) {
					DynamicVO configVO = ConfiguracaoDAO.get();
					String uri = configVO.asString("URL");
					CallService service = new CallService();

					JSONObject body = new JSONObject();
					//JSONObject upProd = new JSONObject();

					if (empresaVO.asString("AD_USALOTEAPPINV") == null) {
						body.put("validate_lote", false);
					} else if (empresaVO.asString("AD_USALOTEAPPINV").equals("S")) {
						body.put("validate_lote", true);
					} else {
						body.put("validate_lote", false);
					}

					body.put("id", linha.getCampo("ID"));
					body.put("company_id", linha.getCampo("CODEMP"));
					body.put("location", linha.getCampo("CODLOCAL"));
					body.put("counting_date", TimeUtils.clearTime((Timestamp) linha.getCampo("DTCONTAGEM"))
							.toLocalDateTime().format(FORMATTER));
					body.put("items", items);

					service.setBody(body.toString());
					service.setMethod("POST");
					service.setUri(uri + "/inventories");

					linha.setCampo("CODUSU", ctx.getUsuarioLogado());
					linha.setCampo("DTENVIO", TimeUtils.getNow());
					linha.setCampo("STATUS", "2");

					service.fire();
					ctx.setMensagemRetorno("Processo concluído!<br>" + items.length() + " itens enviado(s)!");

				} else {
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
		nativeSql.appendSql(
				"SELECT CUSTO FROM AD_CUSTOPROD_INVENT CUS WHERE CUS.CODPROD=:PRODUTO AND CUS.EMPRESA=:EMPRESA");
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

	public void sincronizaProds(BigDecimal id) {

		JSONObject sincProd = new JSONObject();

		JdbcWrapper jdbc = null;
		EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade();
		jdbc = dwfEntityFacade.getJdbcWrapper();
		NativeSql sql = (NativeSql) null;
		ResultSet rs = (ResultSet) null;

		try {

			sql = new NativeSql(jdbc);

			sql.appendSql("SELECT * FROM AD_PRODINVENT WHERE CODPROD = " + id);

			rs = sql.executeQuery();
			
			while (rs.next()) {
				DynamicVO configVO = ConfiguracaoDAO.get();
				String uri = configVO.asString("URL");
				CallService service = new CallService();
				
				sincProd.put("id", rs.getString("CODPROD"));
				sincProd.put("name", rs.getString("DESCRPROD"));
				sincProd.put("brand", rs.getString("MARCA"));
				sincProd.put("barcode", rs.getString("REFERENCIA"));
				sincProd.put("unit_id", rs.getString("CODVOL"));
				sincProd.put("category_id", rs.getString("CODGRUPOPROD"));
				sincProd.put("active", true);
				
				JSONArray unidadesAlternativasArray = new JSONArray();
				
				String queryPk = "CODPROD=" + rs.getString("CODPROD").toString();
				FinderWrapper f = new FinderWrapper("AD_BARINVENT", queryPk);
				
				EntityFacade dwf = EntityFacadeFactory.getDWFFacade();
				Collection<PersistentLocalEntity> rPLES = dwf.findByDynamicFinder(f);
				for (PersistentLocalEntity rPLE : rPLES) {

					EntityVO rEVO = rPLE.getValueObject();
					DynamicVO rVO = (DynamicVO) rEVO;
					JSONObject unidadeAlternativa = new JSONObject();

					unidadeAlternativa.put("unit_id", rVO.asString("CODVOL"));
					unidadeAlternativa.put("barcode", rVO.asString("CODBARRA"));
					unidadeAlternativa.put("divide_or_multiply", rVO.asString("DIVIDEMULTIPLICA"));
					unidadeAlternativa.put("quantity", rVO.asBigDecimal("QUANTIDADE"));

					unidadesAlternativasArray.put(unidadeAlternativa);
				}
				
				sincProd.put("units", unidadesAlternativasArray);
				
				service.setBody(sincProd.toString());

				service.setMethod("POST");
				service.setUri(uri + "/products");

				service.fire();
			}


		} catch (Exception e1) {
			e1.printStackTrace();
		} finally {
			JdbcUtils.closeResultSet(rs);
			NativeSql.releaseResources(sql);
		}

	}

	private boolean verificaSeValidaLote(BigDecimal codemp, BigDecimal codprod) throws Exception {
		boolean valida = false;

		JdbcWrapper jdbcWrapper = null;
		EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade();
		jdbcWrapper = dwfEntityFacade.getJdbcWrapper();
		ResultSet contagem;
		NativeSql nativeSql = new NativeSql(jdbcWrapper);
		nativeSql.resetSqlBuf();

		if (verificaSeExisteMaisDeUmaEmpresaParaOhProduto(codprod)) {
			nativeSql.appendSql(
					"SELECT VALIDATE_LOTE FROM AD_LOTEEMPINVENT WHERE CODPROD=:PRODUTO AND EMPLOTE=:EMPRESA");
			nativeSql.setNamedParameter("PRODUTO", codprod);
			nativeSql.setNamedParameter("EMPRESA", codemp);
		} else {
			nativeSql.appendSql("SELECT VALIDATE_LOTE FROM AD_LOTEEMPINVENT WHERE CODPROD=:PRODUTO AND ROWNUM=1");
			nativeSql.setNamedParameter("PRODUTO", codprod);
		}

		contagem = nativeSql.executeQuery();
		while (contagem.next()) {
			valida = contagem.getBoolean("VALIDATE_LOTE");
		}

		JdbcUtils.closeResultSet(contagem);
		NativeSql.releaseResources(nativeSql);

		return valida;
	}

	private boolean verificaSeExisteMaisDeUmaEmpresaParaOhProduto(BigDecimal codprod) throws Exception {
		boolean existe = false;

		JdbcWrapper jdbcWrapper = null;
		EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade();
		jdbcWrapper = dwfEntityFacade.getJdbcWrapper();
		ResultSet contagem;
		NativeSql nativeSql = new NativeSql(jdbcWrapper);
		nativeSql.resetSqlBuf();
		nativeSql.appendSql("SELECT COUNT(*) AS QTD FROM AD_LOTEEMPINVENT WHERE CODPROD=:PRODUTO");
		nativeSql.setNamedParameter("PRODUTO", codprod);
		contagem = nativeSql.executeQuery();
		while (contagem.next()) {
			int qtd = contagem.getInt("QTD");
			if (qtd > 1) {
				existe = true;
			}
		}

		JdbcUtils.closeResultSet(contagem);
		NativeSql.releaseResources(nativeSql);

		return existe;
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