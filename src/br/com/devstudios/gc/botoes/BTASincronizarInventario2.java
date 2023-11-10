package br.com.devstudios.gc.botoes;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

import org.json.JSONArray;
import org.json.JSONObject;

import com.sankhya.util.JdbcUtils;

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

public class BTASincronizarInventario2 implements AcaoRotinaJava {

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

		if (id != null) {
			JapeWrapper DAO = JapeFactory.dao("AD_ITENSCONTAGENS");
			Collection<DynamicVO> listaItens = DAO.find("this.ID=?", new Object[] { id });
			DynamicVO configVO = ConfiguracaoDAO.get();
			String uri = configVO.asString("URL");
			CallService service = new CallService();

			JSONArray items = new JSONArray();

			for (DynamicVO i : listaItens) {
				JSONObject item = new JSONObject();
				DynamicVO produtoVO = (DynamicVO) EntityFacadeFactory.getDWFFacade()
						.findEntityByPrimaryKeyAsVO(DynamicEntityNames.PRODUTO, i.asBigDecimal("CODPROD"));

				if (!existeCodigoDeBarras(i.asBigDecimal("CODPROD"))) {
					DAO.prepareToUpdate(i).set("STATUS", "F").set("LOG", "PRODUTO SEM CODIGO DE BARRAS").update();
				} else {

				}
			}
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
}