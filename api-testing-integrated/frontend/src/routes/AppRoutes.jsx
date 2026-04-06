import { Routes, Route } from "react-router-dom";
import MainLayout from "../layouts/MainLayout";
import ApiTestingPage from "../pages/ApiTestingPage";
// import ReportsPage from "../pages/ReportsPage";
// import SettingsPage from "../pages/SettingsPage";
// import NotFoundPage from "../pages/NotFoundPage";

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<MainLayout />}>
        <Route index element={<ApiTestingPage />} />
        <Route path="api-testing" element={<ApiTestingPage />} />
        {/* <Route path="reports" element={<ReportsPage />} />
        <Route path="settings" element={<SettingsPage />} /> */}
      </Route>

      {/* <Route path="*" element={<NotFoundPage />} /> */}
    </Routes>
  );
}
