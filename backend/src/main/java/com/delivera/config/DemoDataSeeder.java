package com.delivera.config;

import com.delivera.auth.service.AuthClient;
import com.delivera.depot.dto.AssignRequest;
import com.delivera.depot.dto.UnitRequest;
import com.delivera.depot.model.UnitType;
import com.delivera.depot.repository.OperationalUnitRepository;
import com.delivera.depot.service.UnitClient;
import com.delivera.model.*;
import com.delivera.order.dto.DataOrderRequest;
import com.delivera.order.dto.DataOrderStatusRequest;
import com.delivera.order.model.OrderPriority;
import com.delivera.order.model.OrderStatus;
import com.delivera.order.model.OrderType;
import com.delivera.order.repository.OrderEventRepository;
import com.delivera.order.repository.OrderRepository;
import com.delivera.order.service.OrderClient;
import com.delivera.org.dto.CompanySettingsDTO;
import com.delivera.org.model.Company;
import com.delivera.org.model.Organization;
import com.delivera.org.repository.CompanyRepository;
import com.delivera.org.repository.OrganizationRepository;
import com.delivera.org.service.SettingsClient;
import com.delivera.repository.*;
import com.delivera.service.AdminService;
import com.delivera.space.service.SpaceCompanies;
import com.delivera.space.service.SpaceContracts;
import com.delivera.space.service.SpaceLoyalUsers;
import com.delivera.space.service.SpaceWorkers;
import com.delivera.vehicle.dto.VehicleRequest;
import com.delivera.vehicle.service.VehicleClient;
import com.delivera.worker.model.Worker;
import com.delivera.worker.model.WorkerRole;
import com.delivera.worker.repository.WorkerRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Carga datos de demo cuando el backend arranca con el perfil {@code dev} y la BD no tiene
 * todavía usuarios. Pensado para revisar la aplicación (gráficas, mapas, listados) sin necesidad
 * de crear los datos manualmente desde la UI.
 *
 * Contraseña común para todos los usuarios: {@code demo1234}
 *
 * Credenciales de acceso:
 *   admin@delivera.com  — GLOBAL_ADMIN (acceso a todo)
 *   carlos@rapidlog.com — COMPANY_ADMIN de RapidLog Central y RapidLog Retail
 *   sofia@transnorte.com — COMPANY_ADMIN de TransNorte Logística
 *   paula@transnorte.com — COMPANY_ADMIN de TransNorte Almacenamiento
 *   elena@distrisur.com  — COMPANY_ADMIN de DistriSur Alimentación e Industrial
 *   clara@cliente.com   — usuario registrado con pedidos propios (/my-orders)
 */
@Component
@Profile({"dev", "prod"})
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final SecureRandom RNG = new SecureRandom();

    @Value("${app.demo.seed-password:demo1234}")
    private String seedPassword;

    @Value("${app.demo.seed.auth.host}")
    private String authHost;
    @Value("${app.demo.seed.auth.prefix}")
    private String authPrefix;
    @Value("${app.demo.seed.auth.path}")
    private String authPath;

    @Value("${app.demo.seed.data.host}")
    private String dataHost;
    @Value("${app.demo.seed.data.prefix}")
    private String dataPrefix;

    @Value("${app.demo.populate:false}")
    private boolean demoPopulate = false;

    @Value("${app.demo.reset-on-start:false}")
    private boolean resetOnStart;

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final CompanyRepository companies;
    private final WorkerRepository workers;
    private final OperationalUnitRepository units;
    private final LoyalUserRepository loyalUsers;
    private final OrderRepository orders;
    private final OrderEventRepository orderEvents;
    private final ActivityTypeRepository activityTypes;
    private final SubscriptionPlanRepository plans;
    private final VehicleClient vehicleClient;
    private final AuthClient authClient;
    private final UnitClient unitClient;
    private final SettingsClient settingsClient;
    private final OrderClient orderClient;
    private final AdminService adminService;
    private final SpaceContracts spaceContracts;
    private final SpaceCompanies spaceCompanies;
    private final SpaceWorkers spaceWorkers;
    private final SpaceLoyalUsers spaceLoyalUsers;


    private String adminEmail = "admin@delivera.com";

    @PersistenceContext
    private EntityManager em;

    /* 
    @Autowired
    public DemoDataSeeder(UserRepository users,
                          OrganizationRepository organizations,
                          CompanyRepository companies,
                          WorkerRepository workers,
                          OperationalUnitRepository units,
                          LoyalUserRepository loyalUsers,
                          OrderRepository orders,
                           OrderEventRepository orderEvents,
                           ActivityTypeRepository activityTypes,
                           SubscriptionPlanRepository plans,
                           VehicleClient vehicleClient,
                           UnitClient unitClient,
                           SettingsClient settingsClient,
                           OrderClient orderClient,
                           AdminService adminService,
                           SpaceManagment spaceManagment,
                           SpaceCompanies spaceCompanies,
                           SpaceWorkers spaceWorkers,
                           SpaceLoyalUsers spaceLoyalUsers,
                           AuthClient authClient) {
        this.users = users;
        this.organizations = organizations;
        this.companies = companies;
        this.workers = workers;
        this.units = units;
        this.loyalUsers = loyalUsers;
        this.orders = orders;
        this.orderEvents = orderEvents;
        this.activityTypes = activityTypes;
        this.plans = plans;
        this.vehicleClient = vehicleClient;
        this.unitClient = unitClient;
        this.authClient = authClient;
        this.settingsClient = settingsClient;
        this.orderClient = orderClient;
        this.spaceManagment = spaceManagment;
        this.spaceCompanies = spaceCompanies;
        this.spaceWorkers = spaceWorkers;
        this.spaceLoyalUsers = spaceLoyalUsers;
        this.adminService = adminService;
    }
*/
    @Override
    @Transactional
    public void run(String... args) {
        if (!demoPopulate){
            return;
        }else if (resetOnStart) {
            log.warn("DemoDataSeeder: app.demo.reset-on-start=true -> vaciando tablas de datos antes de re-sembrar.");
            
            wipeData();
        } else if (users.count() > 0) {
            log.info("DemoDataSeeder: la BD ya tiene usuarios, se omite la carga de demo.");
            return;
        }
        this.spaceContracts.removeAllContracts();
        log.info("DemoDataSeeder: cargando datos de demo...");

        // --- 1. Usuarios ---
        User admin  = createUser(adminEmail,    "admin",   "Ana",    "Navarro",   "+34600000001",
                "Paseo de la Castellana 100, Madrid", 40.4430, -3.6900);
        User carlos = createUser("carlos@rapidlog.com",   "carlos",  "Carlos", "García",    "+34600000002",
                "Calle Goya 60, Madrid",              40.4250, -3.6780);
        User lucia  = createUser("lucia@rapidlog.com",    "lucia",   "Lucía",  "Pérez",     "+34600000003",
                "Calle Alcalá 210, Madrid",           40.4310, -3.6590);
        User marcos = createUser("marcos@rapidlog.com",   "marcos",  "Marcos", "Ruiz",      "+34600000004",
                "Av. de América 30, Madrid",          40.4380, -3.6720);
        User sofia  = createUser("sofia@transnorte.com",  "sofia",   "Sofía",  "Martínez",  "+34600000005",
                "Gran Vía 8, Bilbao",                 43.2640, -2.9330);
        User david  = createUser("david@transnorte.com",  "david",   "David",  "Sánchez",   "+34600000006",
                "Calle Alfonso I 15, Zaragoza",       41.6530, -0.8790);
        User paula  = createUser("paula@transnorte.com",  "paula",   "Paula",  "Fernández", "+34600000007",
                "Calle Alta 8, Santander",            43.4620, -3.8100);
        User elena  = createUser("elena@distrisur.com",   "elena",   "Elena",  "Romero",    "+34600000008",
                "Calle Marqués de Larios 5, Málaga",  36.7200, -4.4200);
        User javier = createUser("javier@distrisur.com",  "javier",  "Javier", "Vargas",    "+34600000009",
                "Av. Andaluces 2, Granada",           37.1780, -3.6090);
        User rafael = createUser("rafael@distrisur.com",  "rafael",  "Rafael", "Molina",    "+34600000010",
                "Av. Juan Carlos I 18, Murcia",       37.9930, -1.1320);
        // usuarios "cliente" / fidelizados registrados — con dirección para recibir B2C
        User clara  = createUser("clara@cliente.com",     "clara",   "Clara",  "López",     "+34610000001",
                "Calle Serrano 45, Madrid",           40.4270, -3.6870);
        User raul   = createUser("raul@cliente.com",      "raul",    "Raúl",   "Moreno",    "+34610000002",
                "Av. Andalucía 12, Málaga",           36.7170, -4.4240);
        // usuario "normal" sin roles (para /my-orders)
        createUser("pedro@example.com", "pedro", "Pedro", "Ortiz", "+34610000003",
                "Gran Vía 25, Madrid", 40.4200, -3.7060);

        // --- 2. Organizaciones ---
        Organization delivera   = createOrg("Delivera",    "delivera");   // org de sistema
        Organization rapidlog   = createOrg("RapidLog",    "rapidlog");
        Organization transnorte = createOrg("TransNorte",  "transnorte");
        Organization distrisur  = createOrg("DistriSur",   "distrisur");

        spaceContracts.createContract(carlos, rapidlog,"SMALL");
        spaceContracts.createContract(sofia, transnorte, "SMALL");
        spaceContracts.createContract(elena, distrisur, "SMALL");

        spaceCompanies.companiesConsumption(rapidlog.getId().toString(),2,false);
        spaceCompanies.companiesConsumption(transnorte.getId().toString(),2,false);
        spaceCompanies.companiesConsumption(distrisur.getId().toString(),2,false);

        spaceWorkers.workerConsumption(rapidlog.getId().toString(), 3, false);
        spaceWorkers.workerConsumption(transnorte.getId().toString(), 3, false);
        spaceWorkers.workerConsumption(distrisur.getId().toString(), 3, false);


        // --- 3. Planes y tipos de actividad ---
        ActivityType distribution = activityTypes.findById("DISTRIBUTION").orElseThrow();
        ActivityType food         = activityTypes.findById("FOOD").orElseThrow();
        ActivityType retail       = activityTypes.findById("RETAIL").orElseThrow();
        ActivityType industry     = activityTypes.findById("INDUSTRY").orElseThrow();
        ActivityType transport    = activityTypes.findById("TRANSPORT").orElseThrow();

        SubscriptionPlan free  = plans.findById("FREE").orElseThrow();
        SubscriptionPlan basic = plans.findById("BASIC").orElseThrow();
        SubscriptionPlan pro   = plans.findById("PRO").orElseThrow();

        // --- 4. Empresas ---
        Company deliveraPlatform = createCompany(delivera,   "Delivera Platform",          distribution, free);
        Company rlCentral = createCompany(rapidlog,   "RapidLog Central",           distribution, pro);
        Company rlRetail  = createCompany(rapidlog,   "RapidLog Retail",            retail,       basic);
        Company tnLog     = createCompany(transnorte, "TransNorte Logística",       transport,    pro);
        Company tnStore   = createCompany(transnorte, "TransNorte Almacenamiento",  retail,       basic);
        Company dsFood    = createCompany(distrisur,  "DistriSur Alimentación",     food,         pro);
        Company dsInd     = createCompany(distrisur,  "DistriSur Industrial",       industry,     free);

        // --- 5. Trabajadores ---
        // GLOBAL_ADMIN — vinculado a la empresa de sistema, no a ninguna organización de cliente
        createWorker(admin, deliveraPlatform, WorkerRole.GLOBAL_ADMIN);

        // RapidLog Central — admin, analista y operador
        Worker carlosW = createWorker(carlos, rlCentral, WorkerRole.COMPANY_ADMIN);
        Worker luciaW  = createWorker(lucia,  rlCentral, WorkerRole.ANALYST);
        Worker marcosW = createWorker(marcos, rlCentral, WorkerRole.OPERATOR);

        // RapidLog Retail — mismo admin + analista
        createWorker(carlos, rlRetail, WorkerRole.COMPANY_ADMIN);
        createWorker(lucia,  rlRetail, WorkerRole.ANALYST);

        // TransNorte Logística — admin y operador
        Worker sofiaW = createWorker(sofia,  tnLog, WorkerRole.COMPANY_ADMIN);
        Worker davidW = createWorker(david,  tnLog, WorkerRole.OPERATOR);

        // TransNorte Almacenamiento — admin propio, david como analista compartido
        Worker paulaW = createWorker(paula, tnStore, WorkerRole.COMPANY_ADMIN);
        createWorker(david, tnStore, WorkerRole.ANALYST);

        // DistriSur Alimentación — admin, analista y operador
        Worker elenaW  = createWorker(elena,  dsFood, WorkerRole.COMPANY_ADMIN);
        Worker javierW = createWorker(javier, dsFood, WorkerRole.ANALYST);
        Worker rafaelW = createWorker(rafael, dsFood, WorkerRole.OPERATOR);

        // DistriSur Industrial — mismo admin + analista
        Worker elenaWInd  = createWorker(elena,  dsInd, WorkerRole.COMPANY_ADMIN);
        Worker javierWInd = createWorker(javier, dsInd, WorkerRole.ANALYST);

        // --- 6. Unidades operativas ---
        // RapidLog Central (distribución, varias ciudades)
        UUID rlMadridCd = createUnit(rlCentral, "Centro Madrid",   UnitType.LOGISTICS_CENTER,
                "Av. de la Logística 12, Madrid",        40.4168, -3.7038);
        UUID rlMadridWh = createUnit(rlCentral, "Almacén Getafe",  UnitType.WAREHOUSE,
                "Pol. Ind. Los Olivos, Getafe",          40.3057, -3.7327);
        UUID rlValencia = createUnit(rlCentral, "Centro Valencia", UnitType.LOGISTICS_CENTER,
                "Av. del Puerto 200, Valencia",          39.4699, -0.3763);
        UUID rlSevilla  = createUnit(rlCentral, "Centro Sevilla",  UnitType.LOGISTICS_CENTER,
                "Pol. Ind. Calonge, Sevilla",            37.3891, -5.9845);

        // RapidLog Retail (tiendas)
        UUID rlTiendaMad = createUnit(rlRetail, "Tienda Madrid Sol", UnitType.STORE,
                "Puerta del Sol 4, Madrid",              40.4167, -3.7037);
        UUID rlTiendaBcn = createUnit(rlRetail, "Tienda Barcelona",  UnitType.STORE,
                "Passeig de Gràcia 50, Barcelona",       41.3925,  2.1649);

        // TransNorte Logística (norte peninsular)
        UUID tnBilbao = createUnit(tnLog, "Centro Bilbao",    UnitType.LOGISTICS_CENTER,
                "Av. del Ferrocarril 22, Bilbao",        43.2630, -2.9350);
        UUID tnZgz    = createUnit(tnLog, "Almacén Zaragoza", UnitType.WAREHOUSE,
                "Pol. Malpica, Zaragoza",                41.6488, -0.8891);
        UUID tnVigo   = createUnit(tnLog, "Centro Vigo",      UnitType.LOGISTICS_CENTER,
                "Pol. As Gándaras, Vigo",                42.2406, -8.7207);

        // TransNorte Almacenamiento (Cantabria y Navarra)
        UUID tnSantander = createUnit(tnStore, "Almacén Santander", UnitType.WAREHOUSE,
                "Pol. Ind. Nueva Montaña, Santander",    43.4580, -3.8150);
        UUID tnPamplona  = createUnit(tnStore, "Tienda Pamplona",   UnitType.STORE,
                "Av. de Bayona 2, Pamplona",             42.8180, -1.6430);

        // DistriSur Alimentación (sur, alimentación)
        UUID dsMalaga  = createUnit(dsFood, "Centro Málaga",   UnitType.LOGISTICS_CENTER,
                "Av. Velázquez 180, Málaga",             36.7213, -4.4213);
        UUID dsGranada = createUnit(dsFood, "Almacén Granada", UnitType.WAREHOUSE,
                "Pol. Juncaril, Granada",                37.1773, -3.5986);
        UUID dsMurcia  = createUnit(dsFood, "Tienda Murcia",   UnitType.STORE,
                "Gran Vía 18, Murcia",                   37.9922, -1.1307);

        // DistriSur Industrial (fábrica + almacén)
        UUID dsFactory = createUnit(dsInd, "Fábrica Jerez",  UnitType.FACTORY,
                "Pol. El Portal, Jerez",                 36.6850, -6.1261);
        UUID dsCadizWh = createUnit(dsInd, "Almacén Cádiz",  UnitType.WAREHOUSE,
                "Zona Franca, Cádiz",                    36.5297, -6.2927);

        // --- 7. Asignación de trabajadores a unidades ---
        // RapidLog Central
        assignWorker(rlMadridCd, carlosW);
        assignWorker(rlMadridCd, luciaW);
        assignWorker(rlMadridWh, marcosW);
        assignWorker(rlValencia, luciaW);
        assignWorker(rlValencia, marcosW);
        assignWorker(rlSevilla,  carlosW);
        // TransNorte Logística
        assignWorker(tnBilbao,   sofiaW);
        assignWorker(tnZgz,      davidW);
        assignWorker(tnVigo,     sofiaW);
        // TransNorte Almacenamiento
        assignWorker(tnSantander, paulaW);
        assignWorker(tnPamplona,  paulaW);
        // DistriSur Alimentación
        assignWorker(dsMalaga,   elenaW);
        assignWorker(dsMalaga,   rafaelW);
        assignWorker(dsGranada,  javierW);
        assignWorker(dsMurcia,   rafaelW);
        // DistriSur Industrial
        assignWorker(dsFactory,  elenaWInd);
        assignWorker(dsCadizWh,  javierWInd);

        // --- 8. Vehículos ---
        // RapidLog Central
        createVehicle(rlCentral, rlMadridCd, "RC-001", 1200);
        createVehicle(rlCentral, rlMadridWh, "RC-002", 800);
        createVehicle(rlCentral, rlValencia, "RC-003", 1500);
        // RapidLog Retail
        createVehicle(rlRetail,  rlTiendaMad, "RR-001", 500);
        createVehicle(rlRetail,  rlTiendaBcn, "RR-002", 350);
        // TransNorte Logística
        createVehicle(tnLog,     tnBilbao,    "TN-001", 2000);
        createVehicle(tnLog,     tnZgz,       "TN-002", 1000);
        // TransNorte Almacenamiento
        createVehicle(tnStore,   tnSantander, "TA-001", 600);
        // DistriSur Alimentación
        createVehicle(dsFood,    dsMalaga,    "DS-001", 1500);
        createVehicle(dsFood,    dsGranada,   "DS-002", 900);
        // DistriSur Industrial
        createVehicle(dsInd,     dsFactory,   "DI-001", 2500);
        createVehicle(dsInd,     dsCadizWh,   "DI-002", 1200);

        // --- 9. Fidelizados ---
        LoyalUser luClara   = createLoyalUser(clara.getEmail(), clara, List.of(rlRetail, dsFood),
                null, null, null);
        LoyalUser luRaul    = createLoyalUser(raul.getEmail(),  raul,  List.of(dsFood),
                null, null, null);
        createLoyalUser("maria.gomez@correo.com",        null, List.of(rlRetail),
                "Calle Mallorca 230, Barcelona",  41.3960,  2.1620);
        LoyalUser luIgnacio = createLoyalUser("ignacio.serrano@correo.com", null, List.of(rlRetail, dsFood),
                "Av. Diagonal 500, Barcelona",    41.3920,  2.1510);
        createLoyalUser("teresa.mendez@correo.com",      null, List.of(dsFood),
                "Av. Constitución 3, Granada",    37.1770, -3.6000);
        LoyalUser luAlberto = createLoyalUser("alberto.ibanez@correo.com",  null, List.of(tnLog),
                "Calle Mayor 18, Bilbao",         43.2620, -2.9340);
        createLoyalUser("nuria.vidal@correo.com",        null, List.of(tnLog, tnStore),
                "Calle Ercilla 14, Bilbao",       43.2590, -2.9260);
        LoyalUser luPablo   = createLoyalUser("pablo.castro@correo.com",    null, List.of(rlRetail, tnStore),
                "Calle Pelayo 5, Barcelona",      41.3900,  2.1680);

        spaceLoyalUsers.loyalUserConsumption(rapidlog.getId().toString(),4,false);
        spaceLoyalUsers.loyalUserConsumption(transnorte.getId().toString(),3,false);
        spaceLoyalUsers.loyalUserConsumption(distrisur.getId().toString(),4,false);



        // --- 10. Pedidos ---
        // Internos RapidLog Central
        createInternalOrder(rlCentral, rlMadridCd, rlValencia, OrderStatus.DELIVERED,  OrderPriority.NORMAL, 11, carlos);
        createInternalOrder(rlCentral, rlMadridCd, rlSevilla,  OrderStatus.IN_TRANSIT, OrderPriority.HIGH,    3, marcos);
        createInternalOrder(rlCentral, rlValencia, rlMadridWh, OrderStatus.PENDING,    OrderPriority.NORMAL,  1, lucia);
        createInternalOrder(rlCentral, rlSevilla,  rlMadridCd, OrderStatus.DELIVERED,  OrderPriority.LOW,     9, marcos);
        createInternalOrder(rlCentral, rlMadridWh, rlValencia, OrderStatus.CANCELLED,  OrderPriority.NORMAL,  7, carlos);

        // Internos TransNorte Logística
        createInternalOrder(tnLog, tnBilbao, tnZgz,    OrderStatus.DELIVERED,  OrderPriority.NORMAL, 10, sofia);
        createInternalOrder(tnLog, tnZgz,    tnVigo,   OrderStatus.IN_TRANSIT, OrderPriority.HIGH,    2, david);
        createInternalOrder(tnLog, tnVigo,   tnBilbao, OrderStatus.PENDING,    OrderPriority.NORMAL,  0, sofia);

        // Internos TransNorte Almacenamiento
        createInternalOrder(tnStore, tnSantander, tnPamplona,  OrderStatus.DELIVERED, OrderPriority.NORMAL,  5, paula);
        createInternalOrder(tnStore, tnPamplona,  tnSantander, OrderStatus.PENDING,   OrderPriority.LOW,     0, paula);

        // Internos DistriSur Alimentación
        createInternalOrder(dsFood, dsMalaga,  dsGranada, OrderStatus.DELIVERED,  OrderPriority.LOW,    13, elena);
        createInternalOrder(dsFood, dsMalaga,  dsMurcia,  OrderStatus.IN_TRANSIT, OrderPriority.HIGH,    4, javier);
        createInternalOrder(dsFood, dsGranada, dsMalaga,  OrderStatus.PENDING,    OrderPriority.NORMAL,  0, javier);

        // Internos DistriSur Industrial
        createInternalOrder(dsInd, dsFactory, dsCadizWh, OrderStatus.DELIVERED, OrderPriority.NORMAL,  8, elena);
        createInternalOrder(dsInd, dsCadizWh, dsFactory, OrderStatus.PENDING,   OrderPriority.NORMAL,  0, elena);

        // B2B mismo org: RapidLog Central → Retail
        createB2BOrder(rlCentral, rlMadridCd, rlTiendaMad, OrderStatus.DELIVERED,  OrderPriority.NORMAL, 6, carlos, rlRetail.getName());
        createB2BOrder(rlCentral, rlValencia, rlTiendaBcn, OrderStatus.IN_TRANSIT, OrderPriority.HIGH,   2, lucia, rlRetail.getName());
        createB2BOrder(rlCentral, rlMadridWh, rlTiendaMad, OrderStatus.PENDING,    OrderPriority.NORMAL, 0, marcos, rlRetail.getName());

        // B2B cross-org (distintas organizaciones)
        createB2BOrder(tnLog,     tnBilbao,    rlMadridCd,   OrderStatus.IN_TRANSIT, OrderPriority.NORMAL, 1,  sofia, rlRetail.getName());
        createB2BOrder(rlCentral, rlValencia,  tnVigo,       OrderStatus.PENDING,    OrderPriority.HIGH,   0,  carlos, tnLog.getName());
        createB2BOrder(dsFood,    dsMalaga,    rlTiendaMad,  OrderStatus.DELIVERED,  OrderPriority.NORMAL, 5,  elena, rlCentral.getName());
        createB2BOrder(tnStore,   tnSantander, rlMadridCd,   OrderStatus.DELIVERED,  OrderPriority.NORMAL, 3,  paula, rlCentral.getName());
        createB2BOrder(dsInd,     dsFactory,   tnZgz,        OrderStatus.IN_TRANSIT, OrderPriority.HIGH,   1,  elena, tnLog.getName());
        createB2BOrder(rlCentral, rlSevilla,   dsMalaga,     OrderStatus.PENDING,    OrderPriority.NORMAL, 0,  marcos, dsFood.getName());
        createB2BOrder(tnLog,     tnVigo,      dsFactory,    OrderStatus.DELIVERED,  OrderPriority.LOW,    8,  sofia, dsInd.getName());
        createB2BOrder(dsFood,    dsGranada,   tnSantander,  OrderStatus.IN_TRANSIT, OrderPriority.NORMAL, 2,  javier, tnStore.getName());

        // B2C registrado (loyal user)
        createB2CRegistered(rlRetail, rlTiendaMad, luClara,   OrderStatus.DELIVERED,  OrderPriority.NORMAL, 12, carlos);
        createB2CRegistered(rlRetail, rlTiendaBcn, luIgnacio, OrderStatus.IN_TRANSIT, OrderPriority.HIGH,    3, lucia);
        createB2CRegistered(dsFood,   dsMurcia,    luRaul,    OrderStatus.PENDING,    OrderPriority.NORMAL,  0, javier);
        createB2CRegistered(dsFood,   dsGranada,   luClara,   OrderStatus.DELIVERED,  OrderPriority.LOW,    10, elena);
        createB2CRegistered(tnLog,    tnBilbao,    luAlberto, OrderStatus.IN_TRANSIT, OrderPriority.NORMAL,  2, sofia);
        createB2CRegistered(rlRetail, rlTiendaMad, luClara,   OrderStatus.IN_TRANSIT, OrderPriority.HIGH,    1, marcos);
        createB2CRegistered(rlRetail, rlTiendaBcn, luClara,   OrderStatus.PENDING,    OrderPriority.NORMAL,  0, lucia);
        createB2CRegistered(dsFood,   dsMalaga,    luRaul,    OrderStatus.DELIVERED,  OrderPriority.NORMAL,  6, javier);
        createB2CRegistered(dsFood,   dsGranada,   luRaul,    OrderStatus.IN_TRANSIT, OrderPriority.HIGH,    1, elena);
        createB2CRegistered(tnStore,  tnPamplona,  luPablo,   OrderStatus.DELIVERED,  OrderPriority.NORMAL,  4, paula);
        createB2CRegistered(tnStore,  tnSantander, luPablo,   OrderStatus.PENDING,    OrderPriority.NORMAL,  0, paula);

        // B2C no registrado (email/nombre/dirección)
        createB2CUnregistered(rlRetail, rlTiendaMad, "nuevo.cliente@mail.com", "Jorge Medina",
                "Calle Alcalá 75, Madrid",            40.4200, -3.6950, OrderStatus.PENDING,    OrderPriority.NORMAL, 0,  carlos);
        createB2CUnregistered(rlRetail, rlTiendaBcn, "laura.sanchez@mail.com", "Laura Sánchez",
                "Av. Meridiana 100, Barcelona",       41.4050,  2.1860, OrderStatus.DELIVERED,  OrderPriority.HIGH,   7,  lucia);
        createB2CUnregistered(dsFood,   dsMurcia,    "antonio.duran@mail.com", "Antonio Durán",
                "Av. Libertad 8, Murcia",             37.9890, -1.1290, OrderStatus.IN_TRANSIT, OrderPriority.NORMAL, 2,  elena);
        createB2CUnregistered(dsFood,   dsMalaga,    "carmen.salas@mail.com",  "Carmen Salas",
                "Calle Larios 10, Málaga",            36.7200, -4.4210, OrderStatus.CANCELLED,  OrderPriority.LOW,    5,  javier);
        createB2CUnregistered(tnLog,    tnZgz,       "manuel.lopez@mail.com",  "Manuel López",
                "Paseo Independencia 20, Zaragoza",   41.6510, -0.8840, OrderStatus.DELIVERED,  OrderPriority.NORMAL, 9,  sofia);
        createB2CUnregistered(tnStore,  tnSantander, "rosa.garcia@mail.com",   "Rosa García",
                "Calle Burgos 15, Santander",         43.4610, -3.8080, OrderStatus.IN_TRANSIT, OrderPriority.HIGH,   1,  paula);
        createB2CUnregistered(dsInd,    dsCadizWh,   "felix.romero@mail.com",  "Félix Romero",
                "Av. del Puerto 30, Cádiz",           36.5350, -6.2890, OrderStatus.DELIVERED,  OrderPriority.NORMAL, 6,  javier);
        createB2CUnregistered(rlRetail, rlTiendaBcn, "ana.blanco@mail.com",    "Ana Blanco",
                "Calle Valencia 200, Barcelona",      41.3950,  2.1640, OrderStatus.PENDING,    OrderPriority.NORMAL, 0,  carlos);

        log.info("DemoDataSeeder: demo cargada — {} usuarios, {} empresas, {} unidades, {} pedidos.",
                users.count(), companies.count(), units.count(), orders.count());

    }

    // ----- helpers -----

    @SuppressWarnings("java:S107")
    private User createUser(String email, String username, String first, String last, String phone,
                            String address, Double lat, Double lon) {
        User u = new User();
        u.setEmail(email);
        u.setUsername(username);
        u.setFirstName(first);
        u.setLastName(last);
        u.setPhone(phone);
        if (address != null) u.setAddress(address);
        if (lat != null) u.setLatitude(BigDecimal.valueOf(lat));
        if (lon != null) u.setLongitude(BigDecimal.valueOf(lon));
        var savedUser = users.save(u);
        authClient.registerSeed( 
                u.getId() , 
                email,  
                username,  
                seedPassword,
                authHost+authPrefix+authPath
        ).block();
        return savedUser; 
    }

    private Organization createOrg(String name, String handle) {
        Organization o = new Organization();
        o.setName(name);
        o.setHandle(handle);
        return organizations.save(o);
    }

    private Company createCompany(Organization org, String name, ActivityType activity, SubscriptionPlan plan) {
        Company c = new Company();
        c.setOrganization(org);
        c.setName(name);
        c.setActivityType(activity);
        c.setPlan(plan);

        Company savedCompany = companies.save(c);

        CompanySettingsDTO settingsDTO = new CompanySettingsDTO();
        settingsDTO.setCompanyId(savedCompany.getId());

        String url = dataHost+dataPrefix+"/internal/settings/seed";
        settingsClient.createSeed(settingsDTO, url);
        
        return savedCompany;
    }

    private Worker createWorker(User user, Company company, WorkerRole role) {
        Worker w = new Worker();
        w.setUser(user);
        w.setCompany(company);
        w.setRole(role);
        return workers.save(w);
    }

    private UUID createUnit(Company c, String name, UnitType type, String address,
                                       double lat, double lon) {
        UnitRequest u = new UnitRequest(
                name, 
                type, 
                address, 
                BigDecimal.valueOf(lat),
                BigDecimal.valueOf(lon),
                OrderPriority.NORMAL
        );
        String url = dataHost+dataPrefix+"/internal/units/seed/organizations/"+c.getOrganization().getId()+"/companies/"+c.getId();
        
        return unitClient.createSeed(u, url);
    }

    private void assignWorker(UUID unitId, Worker worker) {
        AssignRequest request = new AssignRequest();
        request.setWorkerId(worker.getId());
        request.setUserId(worker.getUser().getId());
        request.setCompanyId(worker.getCompany().getId());
        String url = dataHost+dataPrefix+"/internal/units/"+unitId+"/seed/assign";
        unitClient.assignSeed(request, url);
    }

    private void createVehicle(Company company, UUID depotId, String plate, int capacity) {

        VehicleRequest vehicleRequest = new VehicleRequest(plate, capacity, depotId);
        String url = dataHost+dataPrefix+"/internal/vehicles/seed/companies/"+company.getId();
        vehicleClient.createSeed(vehicleRequest, url);
       
    }

    private LoyalUser createLoyalUser(String email, User user, List<Company> cs,
                                      String address, Double lat, Double lon) {
        LoyalUser lu = new LoyalUser();
        lu.setEmail(email);
        lu.setUser(user);
        for (Company c : cs) {
            LoyalUserCompany link = lu.linkFor(c);
            if (address != null) link.setAddress(address);
            if (lat != null) link.setLatitude(BigDecimal.valueOf(lat));
            if (lon != null) link.setLongitude(BigDecimal.valueOf(lon));
        }
        return loyalUsers.save(lu);
    }

    // --- Pedidos ---

    private void createInternalOrder(Company c, UUID origin, UUID dest,
                                     OrderStatus status, OrderPriority priority, int daysAgo, User author) {
    
        DataOrderRequest o = newOrder(c, origin, OrderType.INTERNAL, status, priority, daysAgo);
        o.setDestinationId(dest);
        o.setNotes("Pedido interno entre unidades de " + c.getName() + ".");
        backdate(o, daysAgo);
        String url = dataHost+dataPrefix+"/internal/orders/seed";
        UUID orderId =  orderClient.createSeed(o,url);
        addEvents(orderId, c.getId(), status, author);
    }

    private void createB2BOrder(Company c, UUID origin, UUID dest,
                                OrderStatus status, OrderPriority priority, int daysAgo, User author,
                                String destCompanyName
        ) {
        DataOrderRequest o = newOrder(c, origin, OrderType.B2B, status, priority, daysAgo);
        o.setDestinationId(dest);
        o.setNotes("Envío B2B a " + destCompanyName + ".");
        backdate(o, daysAgo);
        String url = dataHost+dataPrefix+"/internal/orders/seed";
        UUID orderId =  orderClient.createSeed(o,url);
        addEvents(orderId, c.getId(),status, author);
    }

    private void createB2CRegistered(Company c, UUID origin, LoyalUser loyal,
                                     OrderStatus status, OrderPriority priority, int daysAgo, User author) {
        DataOrderRequest o = newOrder(c, origin, OrderType.B2C, status, priority, daysAgo);
        o.setLoyalUserId(loyal.getId());
        o.setRecipientEmail(loyal.getEmail());
        o.setRecipientName(loyal.getUser() != null
                ? (loyal.getUser().getFirstName() + " " + loyal.getUser().getLastName())
                : loyal.getEmail());
        // Snapshot de dirección: prioridad link de la empresa origen → user
        LoyalUserCompany link = loyal.findLink(c.getId()).orElse(null);
        String addr = link != null ? link.getAddress() : null;
        BigDecimal lat = link != null ? link.getLatitude() : null;
        BigDecimal lon = link != null ? link.getLongitude() : null;
        if (addr == null && loyal.getUser() != null) {
            addr = loyal.getUser().getAddress();
            lat  = loyal.getUser().getLatitude();
            lon  = loyal.getUser().getLongitude();
        }
        o.setRecipientAddress(addr);
        o.setRecipientLatitude(lat);
        o.setRecipientLongitude(lon);
        o.setNotes("Cliente fidelizado.");
        o.setClaimed(true);
        backdate(o, daysAgo);
        String url = dataHost+dataPrefix+"/internal/orders/seed";
        UUID orderId =  orderClient.createSeed(o,url);
        addEvents(orderId,c.getId(), status, author);
    }

    @SuppressWarnings("java:S107")
    private void createB2CUnregistered(Company c, UUID origin, String email, String name,
                                       String address, double lat, double lon,
                                       OrderStatus status, OrderPriority priority, int daysAgo, User author) {
        DataOrderRequest o = newOrder(c, origin, OrderType.B2C, status, priority, daysAgo);
        o.setRecipientEmail(email);
        o.setRecipientName(name);
        o.setRecipientAddress(address);
        o.setRecipientLatitude(BigDecimal.valueOf(lat));
        o.setRecipientLongitude(BigDecimal.valueOf(lon));
        o.setNotes("Cliente no registrado — envío externo.");
        backdate(o, daysAgo);
        String url = dataHost+dataPrefix+"/internal/orders/seed";
        UUID orderId =  orderClient.createSeed(o,url);
        addEvents(orderId,c.getId(), status, author);
    }

    private DataOrderRequest newOrder(Company c, UUID origin, OrderType type,
                           OrderStatus status, OrderPriority priority, int daysAgo) {
        DataOrderRequest orderRequest = new DataOrderRequest();
        orderRequest.setCurrentCompanyId(c.getId());
        orderRequest.setOriginId(origin);
        orderRequest.setPriority(priority);
        orderRequest.setOrderType(type);
        orderRequest.setReference(nextReference(daysAgo));
        orderRequest.setClaimed(false);

        return orderRequest;
    }

    private String nextReference(int daysAgo) {
        Instant when = Instant.now().minus(daysAgo, ChronoUnit.DAYS);
        String date = when.toString().substring(0, 10).replace("-", "");
        return "ORD-" + date + "-" + String.format("%04d", RNG.nextInt(9999));
    }

    private void backdate(DataOrderRequest o, int daysAgo) {
        Instant when = Instant.now().minus(daysAgo, ChronoUnit.DAYS);
        o.setCreatedAt(when);
        /* 
        em.createNativeQuery("UPDATE orders SET created_at = :ts WHERE id = :id")
                .setParameter("ts", java.sql.Timestamp.from(when))
                .setParameter("id", o.getId())
                .executeUpdate();*/
    } 

    private void addEvents(UUID orderId,UUID companyId, OrderStatus finalStatus, User author) {
        List<OrderStatus> chain = switch (finalStatus) {
            case PENDING    -> List.of(OrderStatus.PENDING);
            case IN_TRANSIT -> List.of(OrderStatus.PENDING, OrderStatus.IN_TRANSIT);
            case DELIVERED  -> List.of(OrderStatus.PENDING, OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED);
            case CANCELLED  -> List.of(OrderStatus.PENDING, OrderStatus.CANCELLED);
        };
        for (OrderStatus s : chain) {
            DataOrderStatusRequest ev = new DataOrderStatusRequest();
            String url = dataHost+dataPrefix+"/internal/orders/"+orderId+"/seed/events";
            ev.setStatus(s);
            ev.setEmail(author.getEmail());
            ev.setCompanyId(companyId);
            ev.setNote(null);
           orderClient.createStatusSeed(ev, url);
        }
    }

    /**
     * Vacía las tablas de datos preservando configuración (activity_types, order_status_config,
     * order_priority_config, worker_role_config, subscription_plans) y el historial de Flyway.
     * Reinicia la secuencia de referencias de pedido.
     */
    private void wipeData() {
        // TODO: RESET DB USES mTLS --> CAN'T COMUNICATE WHEN SERVER STARTS
        //adminService.resetDatabase(adminEmail);
    }
}
